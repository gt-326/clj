(ns onlisp.chap23.common.layer1.core-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]
    [onlisp.common.util3 :as u3]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PATHS・VISITED はグローバルな atom のため、各テスト前後にリセットする。

(defn reset-paths!
  [f]
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{})
  (f)
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{}))


(use-fixtures :each reset-paths!)


;; =====================================================
;; set-register — レジスタ更新マクロ
;; =====================================================
;;
;; (defmacro set-register [k v regs] ...)
;;   k   : クォートされたキー（例: 'subj）
;;   v   : 値のリスト（例: '(spot)）
;;   regs: 現在のレジスタ列（例: '()）
;; → 新規エントリを先頭フレームに追加した新しい regs を返す

(deftest set-register-empty-test
  (testing "空の regs に新規レジスタを追加する"
    (is (= '(((subj spot)))
           (c/set-register 'subj '(spot) '())))))


(deftest set-register-existing-test
  (testing "既存の regs に別キーのレジスタを追加する"
    (is (= '(((v runs) (subj spot)))
           (c/set-register 'v '(runs) '(((subj spot))))))))


;; =====================================================
;; compile-cmds — コマンド列をネストした式に変換する
;; =====================================================
;;
;; (compile-cmds cmds)
;;   cmds が空のとき: シンボル arg_regs を返す（defnode の [pos arg_regs] を参照）
;;   cmds が非空のとき: (first cmds) を先頭に、再帰的にネストさせる

(deftest compile-cmds-empty-test
  (testing "空のコマンド列は arg_regs シンボルを返す"
    (is (= 'arg_regs
           (c/compile-cmds '())))))


(deftest compile-cmds-single-test
  (testing "単一コマンドを arg_regs を末尾に持つ式に変換する"
    (is (= '(setr subj w arg_regs)
           (c/compile-cmds '[(setr subj w)])))))


(deftest compile-cmds-multiple-test
  (testing "複数コマンドをネストした式に変換する"
    (is (= '(setr subj w (pushr v w arg_regs))
           (c/compile-cmds '[(setr subj w) (pushr v w)])))))


;; =====================================================
;; defnode-slow2 — atom による訪問管理
;; =====================================================
;;
;; 生成関数シグネチャ: [pos arg_regs arg_visited]
;;   arg_visited : (atom #{}) — 呼び出し間で共有されるミュータブルな訪問済みセット
;;
;; 特性: swap! による変更はバックトラック後も atom に残るため、
;;       後続アークが同じノードへ再入しようとすると blocked される。

(c/defnode-slow2 slow2-basic-node
  (u/=values :result pos arg_regs))

(def slow2-x-log (atom []))

(c/defnode-slow2 slow2-x-node
  (do
    (swap! slow2-x-log conj :x-visited)
    (u2/fail)))

;; 2つのアークがともに slow2-x-node を呼ぶ
(c/defnode-slow2 slow2-y-node
  (slow2-x-node pos arg_regs arg_visited)
  (slow2-x-node pos arg_regs arg_visited))


(deftest defnode-slow2-not-visited-test
  (testing "未訪問ノードはアークを実行して結果を返す"
    (binding [u/*cont* (fn [a b c] [a b c])]
      (is (= [:result 0 '()]
             (slow2-basic-node 0 '() (atom #{})))))))


(deftest defnode-slow2-visited-test
  (testing "訪問済みノードは即座に failsym を返す"
    (binding [u/*cont* identity]
      (let [visited (atom #{['slow2-basic-node 0]})]
        (is (= u2/failsym
               (slow2-basic-node 0 '() visited)))))))


(deftest defnode-slow2-marks-visited-test
  (testing "ノード呼び出し後に atom へ [name pos] が登録される"
    (binding [u/*cont* (fn [a b c] [a b c])]
      (let [visited (atom #{})]
        (slow2-basic-node 0 '() visited)
        (is (contains? @visited ['slow2-basic-node 0]))))))


(deftest defnode-slow2-no-backtrack-safety-test
  (testing "atom 版: バックトラック後も訪問済みが atom に残り、第2アークが x-node へ再入できない"
    (reset! slow2-x-log [])
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow2-y-node 0 '() (atom #{})))
    ;; arc1 が slow2-x-node を呼んで fail → atom に ['slow2-x-node 0] が登録される
    ;; arc2 が slow2-x-node を呼ぼうとすると既訪問と判定され fail する
    (is (= [:x-visited] @slow2-x-log))))


;; =====================================================
;; defnode-slow3 — immutable set による訪問管理
;; =====================================================
;;
;; 生成関数シグネチャ: [pos arg_regs arg_visited]
;;   arg_visited : #{} — let でシャドウするイミュータブルセット
;;
;; 特性: let によるシャドウで各サンクが独立したスナップショットを保持するため、
;;       バックトラック後の後続アークが同じノードへ再入できる。

(c/defnode-slow3 slow3-basic-node
  (u/=values :result pos arg_regs))

(def slow3-x-log (atom []))

(c/defnode-slow3 slow3-x-node
  (do
    (swap! slow3-x-log conj :x-visited)
    (u2/fail)))

;; 2つのアークがともに slow3-x-node を呼ぶ
(c/defnode-slow3 slow3-y-node
  (slow3-x-node pos arg_regs arg_visited)
  (slow3-x-node pos arg_regs arg_visited))

(def slow3-visited-capture (atom nil))

;; アーク内で arg_visited の値をキャプチャするノード
(c/defnode-slow3 slow3-capture-node
  (do
    (reset! slow3-visited-capture arg_visited)
    (u2/fail)))


(deftest defnode-slow3-not-visited-test
  (testing "未訪問ノードはアークを実行して結果を返す"
    (binding [u/*cont* (fn [a b c] [a b c])]
      (is (= [:result 0 '()]
             (slow3-basic-node 0 '() #{}))))))


(deftest defnode-slow3-visited-test
  (testing "訪問済みノードは即座に failsym を返す"
    (binding [u/*cont* identity]
      (is (= u2/failsym
             (slow3-basic-node 0 '() #{['slow3-basic-node 0]}))))))


(deftest defnode-slow3-arc-sees-updated-visited-test
  (testing "アーク内の arg_visited には [name pos] が let シャドウにより追加済みである"
    (reset! slow3-visited-capture nil)
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow3-capture-node 0 '() #{}))
    (is (contains? @slow3-visited-capture ['slow3-capture-node 0]))))


(deftest defnode-slow3-backtrack-safety-test
  (testing "set 版: 各アークが独立したスナップショットを持ち、第2アークも x-node へ再入できる"
    (reset! slow3-x-log [])
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow3-y-node 0 '() #{}))
    ;; arc1 が slow3-x-node を呼んで fail（x-node の let シャドウは arc1 のスコープ内で完結）
    ;; arc2 は arc1 の let シャドウ前のスナップショットを持つため、x-node への再入が可能
    (is (= [:x-visited :x-visited] @slow3-x-log))))


;; =====================================================
;; defnode-slow4 — immutable set + arg_sent 引数
;; =====================================================
;;
;; 生成関数シグネチャ: [pos arg_regs arg_visited arg_sent]
;;   arg_sent    : 解析対象文（グローバル @SENT 不要）
;;   arg_visited : slow3 と同じ let シャドウ方式

(c/defnode-slow4 slow4-basic-node
  (u/=values :result pos arg_regs))

(def slow4-sent-capture (atom nil))

(c/defnode-slow4 slow4-sent-node
  (do
    (reset! slow4-sent-capture arg_sent)
    (u/=values :captured pos arg_regs)))


(deftest defnode-slow4-not-visited-test
  (testing "未訪問ノードはアークを実行して結果を返す"
    (binding [u/*cont* (fn [a b c] [a b c])]
      (is (= [:result 0 '()]
             (slow4-basic-node 0 '() #{} '(spot runs)))))))


(deftest defnode-slow4-visited-test
  (testing "訪問済みノードは即座に failsym を返す"
    (binding [u/*cont* identity]
      (is (= u2/failsym
             (slow4-basic-node 0 '() #{['slow4-basic-node 0]} '(spot runs)))))))


(deftest defnode-slow4-arg-sent-accessible-test
  (testing "アーク内で arg_sent にアクセスできる"
    (reset! slow4-sent-capture nil)
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow4-sent-node 0 '() #{} '(foo bar)))
    (is (= '(foo bar) @slow4-sent-capture))))


(deftest defnode-slow4-arg-sent-independence-test
  (testing "異なる arg_sent を渡した呼び出しはそれぞれ独立した値をキャプチャする"
    (reset! slow4-sent-capture nil)
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow4-sent-node 0 '() #{} '(a b c)))
    (is (= '(a b c) @slow4-sent-capture))
    (reset! slow4-sent-capture nil)
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow4-sent-node 0 '() #{} '(x y z)))
    (is (= '(x y z) @slow4-sent-capture))))


;; =====================================================
;; defnode-declare — 前方宣言マクロスタブ
;; =====================================================
;;
;; defnode-declare は defnode の本体なしでマクロスタブのみを生成する。
;; (declare =X) + (defmacro X [params] ...) を発行し、
;; 後続のコードで X をマクロとして認識させる。
;;
;; 用途:
;;   循環参照やトポロジカル順序に制約があるノードネットワークで、
;;   本体定義より先に X を参照する別ノードを定義したい場合。
;;
;; (def X nil) との違い:
;;   (def X nil) → Var に nil を設定するだけ（マクロではない）
;;   → 別ノードが X を参照する際、関数呼び出しとしてコンパイルされる
;;   → 後で defnode X を定義してもマクロ関数（4引数）を2引数で呼ぶ → ArityException
;;
;;   defnode-declare → X をマクロとして登録する
;;   → 別ノードが X を参照する際、コンパイル時にマクロ展開される
;;   → 後で defnode X を定義すると =X 関数本体が補完され、実行時も正常動作

;; 1. 前方宣言
(c/defnode-declare decl-forward [pos arg_regs])

;; 2. decl-forward がマクロとして認識された状態で decl-user を定義
(c/defnode decl-user
  (decl-forward (inc pos) arg_regs))

;; 3. 後から本体を定義（=decl-forward 関数が登録される）
(c/defnode decl-forward
  (u/=values :arrived pos arg_regs))


(deftest defnode-declare-creates-macro-test
  (testing "defnode-declare 後は decl-forward がマクロとして登録されている"
    ;; (def X nil) では Var に nil が入るだけでマクロフラグがつかない。
    ;; defnode-declare は defmacro を発行するため :macro true になる。
    ;; これにより別ノードのコンパイル時に (decl-forward pos regs) が
    ;; マクロ展開され、正しく (=decl-forward *cont* pos regs) になる。
    (is (-> #'decl-forward meta :macro)
        "decl-forward Var は :macro フラグを持つ")))


(deftest defnode-declare-forward-ref-test
  (testing "defnode-declare 後に別ノードで参照し、後から本体を定義しても正常にパースできる"
    ;; decl-user は decl-forward の本体定義より先にコンパイルされているが、
    ;; defnode-declare によりマクロ展開済みのため実行時も正常動作する
    (binding [u/*cont* (fn [a b c] [a b c])]
      (is (= [:arrived 1 '()]
             (decl-user 0 '()))))))


;; =====================================================
;; defnode-slow5 — immutable set + arg_sent + u3/*k-fail* 版
;; =====================================================
;;
;; 生成関数シグネチャ: [pos arg_regs arg_visited arg_sent]
;;   - 訪問管理は slow3/4 と同じ let シャドウ方式（arg_visited の immutable set）
;;   - 非決定的選択に u3/true-choose-simple を使う（u2/PATHS への依存なし）
;;   - u3/fail を使うため u3/*k-fail* で失敗継続を管理する

(c/defnode-slow5 slow5-basic-node
  (u/=values :result pos arg_regs))

(def slow5-sent-capture (atom nil))

(c/defnode-slow5 slow5-sent-node
  (do
    (reset! slow5-sent-capture arg_sent)
    (u/=values :captured pos arg_regs)))

(def slow5-x-log (atom []))

(c/defnode-slow5 slow5-x-node
  (do
    (swap! slow5-x-log conj :x-visited)
    (u3/fail)))

;; 2つのアークがともに slow5-x-node を呼ぶ
(c/defnode-slow5 slow5-y-node
  (slow5-x-node pos arg_regs arg_visited arg_sent)
  (slow5-x-node pos arg_regs arg_visited arg_sent))


(deftest defnode-slow5-not-visited-test
  (testing "未訪問ノードはアークを実行して結果を返す"
    (binding [u/*cont* (fn [a b c] [a b c])]
      (is (= [:result 0 '()]
             (slow5-basic-node 0 '() #{} '(spot runs)))))))


(deftest defnode-slow5-visited-test
  (testing "訪問済みノードは即座に u3/failsym を返す"
    (binding [u/*cont* identity]
      (is (= u3/failsym
             (slow5-basic-node 0 '() #{['slow5-basic-node 0]} '(spot runs)))))))


(deftest defnode-slow5-arg-sent-accessible-test
  (testing "アーク内で arg_sent にアクセスできる"
    (reset! slow5-sent-capture nil)
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow5-sent-node 0 '() #{} '(foo bar)))
    (is (= '(foo bar) @slow5-sent-capture))))


(deftest defnode-slow5-backtrack-safety-test
  (testing "set 版: 各アークが独立したスナップショットを持ち、第2アークも x-node へ再入できる"
    ;; slow3/4 と同じ let シャドウ方式のため、arc1 が x-node を訪問済みにしても
    ;; arc2 は arc1 のスナップショット前の arg_visited を持ち、x-node へ再入できる
    (reset! slow5-x-log [])
    (binding [u/*cont* (fn [a b c] [a b c])]
      (slow5-y-node 0 '() #{} '(spot runs)))
    (is (= [:x-visited :x-visited] @slow5-x-log))))
