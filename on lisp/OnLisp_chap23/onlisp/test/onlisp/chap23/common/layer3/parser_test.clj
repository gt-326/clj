(ns onlisp.chap23.common.layer3.parser-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]
    [onlisp.common.util2 :as u2]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PATHS・VISITED はグローバルな atom のため、各テスト前後にリセットする。
;; SENT も念のためリセットする（with-parses-slow4 は @SENT を使わないため
;; テスト間の汚染確認に使う）。

(defn reset-state!
  [f]
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{})
  (reset! o/SENT [])
  (f)
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{})
  (reset! o/SENT []))


(use-fixtures :each reset-state!)


;; =====================================================
;; with-parses — defnode + category + up
;; =====================================================
;;
;; パース開始前に @o/SENT と @u2/PATHS をリセットする。
;; ノードは defnode（2引数: [pos arg_regs]）、
;; 遷移は category（次ノードに pos と compiled-regs を渡す）を使う。

(c/defnode parser-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode parser-verb
  (o/category verb parser-end (r/setr v ***)))

(c/defnode parser-noun
  (o/category noun parser-verb (r/setr subj ***)))


(deftest with-parses-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses parser-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses parser-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))


(deftest with-parses-sets-sent-test
  (testing "with-parses は @o/SENT を sent の値に設定する"
    (p/with-parses parser-noun '(spot runs) nil)
    (is (= '(spot runs) @o/SENT))))


;; =====================================================
;; with-parses-slow — defnode-slow + category + up
;; =====================================================
;;
;; with-parses に加えて @u2/VISITED をリセットする。
;; ノードは defnode-slow（2引数: [pos arg_regs]）を使う。
;; 訪問済みチェックはグローバルな @u2/VISITED atom で管理される。

(c/defnode-slow parser-slow-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode-slow parser-slow-verb
  (o/category verb parser-slow-end (r/setr v ***)))

(c/defnode-slow parser-slow-noun
  (o/category noun parser-slow-verb (r/setr subj ***)))


(deftest with-parses-slow-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses-slow parser-slow-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-slow-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses-slow parser-slow-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))


(deftest with-parses-slow-clears-visited-test
  (testing "実行前に @u2/VISITED をリセットするので、汚染済み VISITED でもパースは正常に動作する"
    ;; parser-slow-noun の [pos=0] を既訪問としてマークしておく
    (reset! u2/VISITED #{['parser-slow-noun 0]})
    (let [results (atom [])]
      (p/with-parses-slow parser-slow-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


;; =====================================================
;; with-parses-slow2 — defnode-slow2 + category2 + up
;; =====================================================
;;
;; with-parses-slow2 は arg_visited の初期値として (atom #{}) を渡す。
;; ノードは defnode-slow2（3引数: [pos arg_regs arg_visited]）、
;; 遷移は category2（次ノードに pos・compiled-regs・arg_visited を渡す）を使う。
;; 訪問管理は @VISITED グローバルではなく、パース呼び出し単位のローカル atom になる。

(c/defnode-slow2 parser-slow2-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode-slow2 parser-slow2-verb
  (o/category2 verb parser-slow2-end (r/setr v ***)))

(c/defnode-slow2 parser-slow2-noun
  (o/category2 noun parser-slow2-verb (r/setr subj ***)))


(deftest with-parses-slow2-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses-slow2 parser-slow2-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-slow2-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses-slow2 parser-slow2-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))


;; =====================================================
;; with-parses-slow3 — defnode-slow3 + category2 + up
;; =====================================================
;;
;; with-parses-slow3 は arg_visited の初期値として #{} を渡す（slow2 の atom 版に対し immutable set 版）。
;; ノードは defnode-slow3（3引数: [pos arg_regs arg_visited]）、
;; 遷移は category2（次ノードに arg_visited を渡す）を使う。
;; let シャドウで各サンクが独立したスナップショットを保持するため、バックトラック安全。

(c/defnode-slow3 parser-slow3-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode-slow3 parser-slow3-verb
  (o/category2 verb parser-slow3-end (r/setr v ***)))

(c/defnode-slow3 parser-slow3-noun
  (o/category2 noun parser-slow3-verb (r/setr subj ***)))


(deftest with-parses-slow3-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses-slow3 parser-slow3-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-slow3-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses-slow3 parser-slow3-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))


;; =====================================================
;; with-parses-slow4 — defnode-slow4 + category3 + up
;; =====================================================
;;
;; with-parses-slow4 は @o/SENT を使わず、sent を arg_sent として直接渡す。
;; ノードは defnode-slow4（4引数: [pos arg_regs arg_visited arg_sent]）、
;; 遷移は category3（次ノードに arg_visited と arg_sent を渡す）を使う。
;; SENT グローバル atom への依存がゼロになる。

(c/defnode-slow4 parser-slow4-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode-slow4 parser-slow4-verb
  (o/category3 verb parser-slow4-end (r/setr v ***)))

(c/defnode-slow4 parser-slow4-noun
  (o/category3 noun parser-slow4-verb (r/setr subj ***)))


(deftest with-parses-slow4-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses-slow4 parser-slow4-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-slow4-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses-slow4 parser-slow4-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))


(deftest with-parses-slow4-sent-independence-test
  (testing "@o/SENT に別の値が設定されていても引数の sent でパースする"
    ;; with-parses-slow4 は @o/SENT を参照しないため、この汚染は無視される
    (reset! o/SENT '(wrong sentence here))
    (let [results (atom [])]
      (p/with-parses-slow4 parser-slow4-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


;; =====================================================
;; with-parses — jump
;; =====================================================
;;
;; jump はトークンを消費せず、直接次ノードへ遷移する。
;; jump 展開: (~next ~'pos ~(c/compile-cmds cmds))
;;   → (next-node pos (r/setr key val arg_regs)) のようにコンパイル時に cmds が展開される
;;
;; 修正前: (c/gen-reg-data '~cmds) → gen-reg-data は存在しない関数
;; 修正後: ~(c/compile-cmds cmds)  → コンパイル時にコマンド列を展開

(c/defnode jtest-end
  (o/up `(~'jumped ~(r/getr jval))))

(c/defnode jtest-start
  (o/jump jtest-end (r/setr jval nil)))


(deftest with-parses-jump-test
  (testing "jump で空文をパースし、セットしたレジスタの値が結果に含まれる"
    ;; jtest-start は jval = nil をセットして jtest-end へ jump
    ;; 空文（pos=0、count=0）でも成立する
    (let [results (atom [])]
      (p/with-parses jtest-start '()
        (swap! results conj parse))
      (is (= ['(jumped nil)] @results)))))


;; =====================================================
;; with-parses — down
;; =====================================================
;;
;; down はサブネットワークを呼び出す。
;; サブネットワークが up した値が *** に束縛されて次ノードへ渡る。
;;
;; 修正前: (u/=bind [~'star ~'pos ~'regs] ...)
;;   → star は *** と一致しない、regs は arg_regs と一致しない
;; 修正後: (u/=bind [~'*** ~'pos ~'arg_regs] ...)
;;   → *** が up の結果を受け取る、arg_regs が compile-cmds の終端と一致する

(c/defnode dtest-sub-end
  (o/up (r/getr ditem)))

(c/defnode dtest-sub
  (o/category n dtest-sub-end (r/setr ditem ***)))

(c/defnode dtest-main-end
  (o/up `(~'found ~(r/getr dword))))

(c/defnode dtest-main
  (o/down dtest-sub dtest-main-end (r/setr dword ***)))


(deftest with-parses-down-test
  (testing "down でサブネットワークを呼び出し、up した値が *** に束縛されて次ノードへ渡る"
    ;; dtest-sub は n カテゴリの単語をパースして up → *** に束縛
    ;; dtest-main-end は *** を dword レジスタに格納して up
    (let [results (atom [])]
      (p/with-parses dtest-main '(arrow)
        (swap! results conj parse))
      (is (= ['(found arrow)] @results)))))


(deftest with-parses-down-no-match-test
  (testing "サブネットワークが失敗すれば down 全体も失敗する"
    ;; 'the' は det カテゴリのみで n でない → dtest-sub が失敗 → 結果なし
    (let [results (atom [])]
      (p/with-parses dtest-main '(the)
        (swap! results conj parse))
      (is (= [] @results)))))


;; =====================================================
;; with-parses-slow2 — down2 + up（回帰テスト）
;; =====================================================
;;
;; down2 の =bind が [*** pos arg_regs arg_visited]（4引数）のままだと、
;; up が (*cont* result pos (rest arg_regs)) と3引数で呼んだ際に
;; ArityException が発生する。
;;
;; 修正後（3引数 bind）の動作を確認するリグレッションテスト。
;; atn6 の '(arrows) パース失敗と同じ構造:
;;   down2 でサブネットワークを呼び出し → sub 内の terminal node が up → *** に束縛

(c/defnode-slow2 d2-sub-end
  (o/up (r/getr ditem)))

(c/defnode-slow2 d2-sub
  (o/category2 n d2-sub-end (r/setr ditem ***)))

(c/defnode-slow2 d2-main-end
  (o/up `(~'found ~(r/getr dword))))

(c/defnode-slow2 d2-main
  (o/down2 d2-sub d2-main-end (r/setr dword ***)))


(deftest with-parses-slow2-down2-test
  (testing "down2 経由で呼ばれた sub-network の up が正常に動作する（down2 アリティ不一致の回帰テスト）"
    (let [results (atom [])]
      (p/with-parses-slow2 d2-main '(arrow)
        (swap! results conj parse))
      (is (= ['(found arrow)] @results)))))


(deftest with-parses-slow2-down2-no-match-test
  (testing "サブネットワークが失敗すれば down2 全体も失敗する"
    (let [results (atom [])]
      (p/with-parses-slow2 d2-main '(the)
        (swap! results conj parse))
      (is (= [] @results)))))


;; =====================================================
;; with-parses-slow5 — defnode-slow5 + category4 + up
;; =====================================================
;;
;; with-parses-slow5 は u3/*k-fail* を使う（u2/PATHS への依存なし）。
;; ノードは defnode-slow5（4引数: [pos arg_regs arg_visited arg_sent]）、
;; 遷移は category4（次ノードに arg_visited と arg_sent を渡す）を使う。
;; with-parses-slow4 と同じシグネチャだが、内部の失敗継続が u3 ベースになる。

(c/defnode-slow5 parser-slow5-end
  (o/up `(~'sentence
          (~'subject ~(r/getr subj))
          (~'verb ~(r/getr v)))))

(c/defnode-slow5 parser-slow5-verb
  (o/category4 verb parser-slow5-end (r/setr v ***)))

(c/defnode-slow5 parser-slow5-noun
  (o/category4 noun parser-slow5-verb (r/setr subj ***)))


(deftest with-parses-slow5-success-test
  (testing "'(spot runs) が正常にパースされ parse ツリーが収集される"
    (let [results (atom [])]
      (p/with-parses-slow5 parser-slow5-noun '(spot runs)
        (swap! results conj parse))
      (is (= ['(sentence (subject spot) (verb runs))]
             @results)))))


(deftest with-parses-slow5-no-match-test
  (testing "カテゴリ順が合わない '(runs spot) は parse 結果なし"
    (let [results (atom [])]
      (p/with-parses-slow5 parser-slow5-noun '(runs spot)
        (swap! results conj parse))
      (is (= [] @results)))))
