(ns msa.db.ds
  "Datascript interaction including persistence"
  (:require [datascript.core :as d]
            [datascript.transit :as dt]))

(def schema {:aka {:db/cardinality :db.cardinality/many}
             :family {:db/cardinality :db.cardinality/many
                      :db/valueType   :db.type/ref}})
(def conn (d/create-conn schema))

;; Negative IDs are used to reference records in the same transaction
(d/transact! conn [{:db/id -1 :name "Maksim" :age 45 :aka ["Max Otto von Stierlitz", "Jack Ryan"]}
                   {:db/id -2 :name "Jade" :age 19 :aka ["jadeypoe"]}
                   {:db/id -3 :name "Skyler" :age 19 :aka ["sky"]}
                   {:db/id -4 :name "Jasmin" :age 13 :aka ["jazzy"]}
									 {:db/id -5 :name "Cornelis" :age 54 :kids 6 :married true :aka  ["Rico"] :family  [-2 -3 -4]}])

(d/transact conn [{:db/id -1 :name "Cornelis" :age 54 :hair "brown" :aka ["Rico"] :family [2 3 4] }])
(d/transact conn [{:db/add 5 :eyes "hazel"}])

;; Print DB to edn
(pr-str @conn)

;; Read DB from edn string
(clojure.edn/read-string {:readers d/data-readers} (pr-str @conn))

;; Write DB to transit
(dt/write-transit-str @conn)

;; Read DB from transit
(def transit-conn (d/conn-from-db (-> @conn (dt/write-transit-str) (dt/read-transit-str))))
(pr-str @transit-conn)

(defn write-edn-db []
  (spit "./db.edn"(pr-str @conn)))

(defn read-edn-db []
  (d/conn-from-db (slurp "./db.edn")))

(defn write-transit-db "doc-string" []
  (spit "./db.transit" (dt/write-transit-str @conn)))

(defn read-transit-db []
  (d/conn-from-db (dt/read-transit-str (slurp "./dn.transit"))))

(write-edn-db)
(write-transit-db)

(def ednf-conn (read-edn-db))
(def transitf-conn (read-edn-db))

(pr-str @ednf-conn)
(pr-str @transitf-conn)

(d/q '[:find  ?n ?a
       :where [?e :aka "Max Otto von Stierlitz"]
              [?e :name ?n]
              [?e :age  ?a]]
       @conn)

; Find tuples containing name and age.
(d/q '[:find  ?n ?a
       :where [?e :name ?n]
              [?e :age  ?a]]
       @conn)

; Pull all attributes
(d/q '[:find (pull ?e [*])
       :where [?e :name "Cornelis"]]
       @conn)

; Pull listed attributes if available
(d/q '[:find (pull ?e [:name :married :hair :nep])
       :where [?e :name "Cornelis"]]
       @conn)

; Provide query arguments and bind in the :in clause. More performant as query can be cached.
; $ references the DB. Can reference multiple DBs with $1, $2 etc. Binding variables follow $.
(d/q '[:find (pull ?e [*])
       :in $ ?alias ?married?
       :where [?e :aka ?alias]
              [?e :married ?married?]]
       @conn "Rico" true)

(d/q '[:find  ?n ?a
       :where [?e :name ?n]
              [?e :age  ?a]
              [?e :family ?f]
              [?f :aka "jazzy"]]
       @conn)

(d/q '[:find (pull ?e [*])
       :in $ ?alias
       :where [?e :family ?f]
              [?f :aka ?alias]]
       @conn "jazzy")

(d/pull @conn '[*] 5)

(->> (d/datoms @conn :eavt)
     (seq)
     #_(first)
     (println))
