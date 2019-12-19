(ns my-hugsql.adapter.next-jdbc
  "next.jdbc adapter for HugSQL."
  (:gen-class)
  (:require [hugsql.adapter :as adapter]
            [next.jdbc :as jdbc]))

(deftype HugsqlAdapterNextJdbc []

  adapter/HugsqlAdapter
  (execute [this db sqlvec {:keys [command-options] :as options}]
    (jdbc/execute! db sqlvec
                   (if (some #(= % (:command options)) [:insert :i!])
                     (assoc command-options :return-keys true)
                     command-options)))

  (query [this db sqlvec options]
    (jdbc/execute! db sqlvec (:command-options options)))

  (result-one [this result options]
    (first result))

  (result-many [this result options]
    result)

  (result-affected [this result options]
    (:next.jdbc/update-count (first result)))

  (result-raw [this result options]
    result)

  (on-exception [this exception]
    (throw exception)))

(defn my-hugsql-adapter-next-jdbc []
  (->HugsqlAdapterNextJdbc))
