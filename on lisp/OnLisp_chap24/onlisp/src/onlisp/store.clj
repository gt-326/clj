(ns onlisp.store)


(defn make-db
  []
  {})


(def ^:dynamic *default-db* (atom (make-db)))


(defn clear-db
  ([]
   (clear-db *default-db*))
  ([db]
   (reset! db {})))


(defn db-query
  ([key]
   (db-query key *default-db*))
  ([key db]
   {:val (@db key) :found (coll? (@db key))}))


(defn db-push
  ([key val]
   (db-push key val *default-db*))
  ([key val db]
   (swap! db assoc key (conj ((db-query key) :val) val))))


(defmacro fact
  [pred & args]
  `(do (db-push '~pred '~args)
       '~args))


;; ================


(defn gen-facts
  []
  ;; init
  (clear-db)

  ;; painter
  (fact painter canale antonio venetian)
  (fact painter hogarth william english)
  (fact painter reynolds joshua english)

  ;; dates
  (fact dates canale 1697 1768)
  (fact dates hogarth 1697 1772)
  (fact dates reynolds 1723 1792))
