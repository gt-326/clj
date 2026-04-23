(ns onlisp.common.util3
  (:require
    [onlisp.common.util :as u]))


;; [ P301 chap22.4 ] — PATHS なし版
;;
;; PATHS atom（グローバルな失敗継続スタック）を廃止し、
;; ^:dynamic *k-fail* で失敗継続を管理する。
;;
;; 呼び出しインターフェースは util2 と同一に保つ:
;;   fail / cb / choose-bind / choose / true-choose-simple / true-choose2
;;
;; 主な変化:
;;   util2: (swap! PATHS conj (fn [] next-choice))
;;   util3: (binding [*k-fail* (fn [] next-choice)] ...)
;;
;; 効果:
;;   - グローバルな可変状態がゼロになる
;;   - *k-fail* はスレッドローカルのため並列パースが可能になる
;;
;; =====================================================
;; *k-fail* 実装の核心的な設計原則
;; =====================================================
;;
;; PATHS（util2）と *k-fail*（util3）の本質的な差異:
;;
;;   PATHS: スタック。fail で pop → 取り出した thunk は消費済みになる。
;;   *k-fail*: 動的バインディング。スコープが続く限り有効なまま持続する。
;;
;; この差異により、=bind 継続の内部から fail を呼ぶと循環が起きる:
;;
;;   binding[*k-fail* = K]
;;     arc1 → fail → K が呼ばれる（ここまでは util2 と同じ）
;;       arc2 → *cont* = k が呼ばれる
;;         k の内部: fail
;;           → (*k-fail*) = K がまだスコープ内にある → 無限ループ！
;;
;; 解決策: cb / true-choose-simple / true-choose-impl の全関数で、
;; バインディング設定前に外側の *k-fail* を saved-k-fail として保存し、
;; クロージャ内・最終選択肢の両方で saved-k-fail を使う。
;;
;; これにより、各選択肢が使用された後は外側の *k-fail* へ正しく移行する。

;; (def PATHS (atom []))  ;; 廃止

(def failsym '[end])


;; =====================================================
;; 失敗継続 — PATHS の代替
;; =====================================================

;; 初期値は「これ以上選択肢がない」を示す failsym を返すクロージャ
(def ^:dynamic *k-fail* (fn [] failsym))


;; fail: 現在の *k-fail* を呼ぶ
;;
;; util2 との差分: 引数なし（インターフェース同一）
;;   util2: PATHS から pop して呼ぶ（pop により thunk が消費される）
;;   util3: *k-fail* を直接呼ぶ（binding スコープにより持続している）
(defn fail
  []
  (*k-fail*))


;; =====================================================
;; cb — choose-bind の実装
;; =====================================================
;;
;; util2.cb との対比:
;;   util2: (swap! PATHS conj (fn [] (binding [*cont* saved] (cb fnc (rest choices)))))
;;   util3: (binding [*k-fail* K] ...) ただし K には saved-k-fail を含む（後述）
;;
;; 単一選択肢のとき:
;;   util2: (rest '(x)) = () が truthy → 常に PATHS に push
;;   util3: (seq (rest [x])) = nil    → push しない（Common Lisp に近い挙動）
;;
;; saved-k-fail の役割:
;;   選択肢が 2 つ以上あるとき: クロージャ内で *k-fail* を saved-k-fail に戻す。
;;   最終選択肢のとき        : *k-fail* を saved-k-fail に設定して実行する。
;;   これにより、選択肢が使い切られた後は外側の *k-fail* へ正しく委譲される。
(defn cb
  [fnc choices]
  (if (seq choices)
    (let [saved-cont   u/*cont*
          saved-k-fail *k-fail*]
      (binding [*k-fail* (if (seq (rest choices))
                           (fn []
                             (binding [u/*cont*  saved-cont
                                       *k-fail* saved-k-fail]
                               (cb fnc (rest choices))))
                           saved-k-fail)]
        (fnc (first choices))))
    (fail)))


;; =====================================================
;; choose-bind マクロ
;; =====================================================
;;
;; util2 と同じインターフェース。
;; *k-fail* は cb が内部で管理するため、ユーザーは意識不要。
(defmacro choose-bind
  [param choices & body]
  `(cb (fn [~param] ~@body) ~choices))


;; =====================================================
;; true-choose-simple — 訪問管理なしのシンプルな非決定的選択
;; =====================================================
;;
;; util2 との対比:
;;   util2: thunk を PATHS に push → fail で pop（消費）→ 再呼び出しなし
;;   util3: *k-fail* に binding  → スコープが続く限り有効
;;          saved-k-fail により使用済み選択肢の再呼び出しを防ぐ
;;
;; defnode-slow5 のアーク実行に使われる。
;;
;; saved-k-fail の役割は cb と同様（上記参照）。
(defn true-choose-simple
  [choices]
  (if (empty? choices)
    (fail)
    (let [[a & more] choices
          saved-cont   u/*cont*
          saved-k-fail *k-fail*]
      (binding [*k-fail* (if (seq more)
                           (fn []
                             (binding [u/*cont*  saved-cont
                                       *k-fail* saved-k-fail]
                               (true-choose-simple more)))
                           saved-k-fail)]
        (a)))))


;; =====================================================
;; choose マクロ — 先頭を直接実行し残りを *k-fail* に積む
;; =====================================================
;;
;; util2.choose は PATHS に直接 push していたが、
;; util3 では true-choose-simple に委譲して統一する。
;;
;; util2 との対比:
;;   util2: (swap! PATHS conj #(arc2)) → PATHS push + arc1 実行
;;   util3: (true-choose-simple [(fn [] arc1) (fn [] arc2)]) → 委譲
(defmacro choose
  [& choices]
  (if (seq choices)
    `(true-choose-simple
       (list ~@(map (fn [c] `(fn [] ~c)) choices)))
    `(fail)))


;; =====================================================
;; true-choose2 マクロ — true-choose-simple のマクロ版ラッパー
;; =====================================================
(defmacro true-choose2
  [& choices]
  `(true-choose-simple
     (list ~@(map (fn [c] `(fn [] ~c)) choices))))


;; =====================================================
;; true-choose-impl / true-choose — visited による循環回避
;; =====================================================
;;
;; VISITED global atom は PATHS とは独立しているため構造は util2 と同様。
;; PATHS push の部分を *k-fail* binding に置き換え、
;; さらに saved-k-fail による外側委譲パターンを適用する。
(defn true-choose-impl
  [visited choices]
  (if (empty? choices)
    (fail)
    (let [[a & more] choices
          saved-cont   u/*cont*
          saved-k-fail *k-fail*]
      (if (contains? visited a)
        (true-choose-impl (conj visited a) more)
        (binding [*k-fail* (if (seq more)
                             (fn []
                               (binding [u/*cont*  saved-cont
                                         *k-fail* saved-k-fail]
                                 (true-choose-impl (conj visited a) more)))
                             saved-k-fail)]
          (a))))))


(defn true-choose
  [choices]
  (true-choose-impl #{} choices))


(defmacro true-choose_
  [& choices]
  `(true-choose-impl #{}
                     (list ~@(map (fn [c] `(fn [] ~c)) choices))))


;; =====================================================
;; VISITED — cross-node サイクル防止（util2 と同一）
;; =====================================================
;;
;; VISITED は PATHS とは独立した仕組みのため、そのまま保持する。

(def VISITED (atom #{}))


(defn node-visited?
  [node-info]
  (contains? @VISITED node-info))


(defn mark-visited!
  [node-info]
  (swap! VISITED conj node-info))


;; =====================================================
;; mark / cut — [ P307 chap22.5 ]
;; =====================================================
;;
;; PATHS 版の意味:
;;   mark: fail 関数自体を番兵として PATHS に push
;;   cut : 番兵（fail）に達するまで PATHS を pop
;;
;; *k-fail* 版の設計:
;;   mark: 現在の *k-fail* を CUT-POINT に保存する
;;   cut : *k-fail* を CUT-POINT まで巻き戻す（以降の選択肢を破棄）
;;
;; 制約:
;;   set! は binding フレーム内でのみ有効。
;;   cut は cb / true-choose-simple の binding スコープ内から
;;   呼ばれることを前提とする（ATN パーサーの arcs 内での使用に対応）。
;;
;; 現在の制限:
;;   CUT-POINT は atom のためスレッドセーフではない。
;;   ネストした mark/cut も1段のみ対応。
;;   ATN パーサー（chap23）では mark/cut は使われないため、当面は試作レベル。

(def CUT-POINT (atom (fn [] failsym)))


(defn mark
  []
  (reset! CUT-POINT *k-fail*))


(defn cut
  []
  ;; *k-fail* を binding したスコープ内から呼ばれることが前提
  (set! *k-fail* @CUT-POINT))
