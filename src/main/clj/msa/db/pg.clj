(ns msa.db.pg
  "Core PostgreSQL database interaction"
  {:clj-kondo/config
   '{;:lint-as {msa.db.pg/defstate clojure.core/def}
     :linters {:unresolved-symbol {:level :off :exclude [(msa.db.pg [sink-db])]}}}}

  (:require [config.env :as cfg]
            [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [mount.core :as mount]
            [timbre.appenders.bunyan :as log])
  (:import (com.zaxxer.hikari HikariDataSource))
  (:gen-class))

;; A generic data pool
(defn pooled-datasource []
  (let [pds (doto (HikariDataSource.)
              (.setJdbcUrl (cfg/db-url))
              (.setUsername (cfg/db-user))
              (.setPassword (cfg/db-password))
              (.setMaximumPoolSize 5)
              ; 24 hours for a uS
              (.setConnectionTimeout 86400000)
              ; 1 minute
              (.setIdleTimeout 60000))]
    pds))

(defn ds-close
  "Close a Hikari datasource"
  [ds]
  (.close ^HikariDataSource ds))

; Aggregation sink DB. Target set through environment.
(mount/defstate sink-db
  :start (do
           (when (cfg/run-dev-mode)
             (log/debug (format "jdbcUrl is %s" (cfg/db-url))))
           (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc {:builder-fn next.jdbc.result-set/as-maps}))
           (pooled-datasource))
  :stop (ds-close sink-db))
