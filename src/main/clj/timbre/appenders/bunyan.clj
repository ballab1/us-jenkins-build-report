(ns timbre.appenders.bunyan
  "Create a Node.js bunyan compatible logger logging in JSON format for data mining by e.g. Elastic Search.
  To view generated logs in human friendly format use the the bunyan CLI utility.
  Simply install with 'npm i -g bunyan'
  Then 'run dev | bunyan' or 'bunyan some-log-file' "
  (:require [cheshire.core :as json]
            [config.env :as cfg]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rolling :as rol-app]
            [taoensso.timbre.appenders.3rd-party.rotor :as rot-app]
            )
  (:gen-class))

(def bunyan-levels
  "Maps a logging level keyword into a bunyan integer level"
  {:trace 10
   :debug 20
   :info 30
   :warn 40
   :error 50
   :fatal 60})

(def app-name
  "Get cached app-name"
  (memoize (fn [data] (str (get-in data [:config :app-name]) "-" (cfg/revision)))))

(def currentPID
  "Get current process PID"
  (memoize
    (fn []
      (-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
          (.getName)
          (string/split #"@")
          (first)))))

(defn bunyan-log-output
  "Function which formats a log command into a bunyan compatible json string",
  ([data] (bunyan-log-output nil data))
  ([opts data]
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [pid level ?err msg_ ?ns-str ?file hostname_ timestamp_ ?line]} data
         stack (when-not no-stacktrace? (when-let [err ?err] {:stack (log/stacktrace err opts)}))]
     #_(println opts)
     #_(println data)
     (json/generate-string (merge (:context data)
                            {:v 0
                             :name (app-name data)
                             :pid (currentPID)
                             :hostname (force hostname_)
                             :time (force timestamp_)
                             :msg (force msg_)
                             :level (level bunyan-levels)}
                            (if (some? ?line) {:src {:file ?file :line ?line :namespace ?ns-str}} {})
                            (if (some? stack) {:err stack} {}))))))

(def iso-pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(defn bunyan-appender
  "Returns a Timbre appender which emits bunyan compatible json strings to stdout"
  []
  (assoc (taoensso.timbre.appenders.core/println-appender)
         :timestamp-opts {:pattern iso-pattern}
         :output-fn bunyan-log-output
         :min-level (cfg/timbre-log-level)))

(defn bunyan-spit-appender
  "Returns a Timbre appender which emits bunyan compatible json strings to a log-file"
  [log-file]
  (assoc (taoensso.timbre.appenders.core/spit-appender {:fname log-file})
         :timestamp-opts {:pattern iso-pattern}
         :output-fn bunyan-log-output))

(defn bunyan-rolling-appender
  "Returns a Timbre appender which emits bunyan compatible json strings to a daily rolling log-file"
  [log-file]
  (assoc (rol-app/rolling-appender {:path log-file})
         :timestamp-opts {:pattern iso-pattern}
         :output-fn bunyan-log-output))

(defn bunyan-rotor-appender
  "Returns a rotating file appender."
  [& [{:keys [path max-size backlog]
       :or   {max-size (* 1024 1024 5)
              backlog 5}}]]
  (assoc (rot-app/rotor-appender {:path path :max-size max-size :backlog backlog})
         :timestamp-opts {:pattern iso-pattern}
         :output-fn bunyan-log-output
         :min-level (cfg/timbre-log-level)))
;
; Configure timbre bunyan logging
;

; name of the app logged in the JSON 'name' field.
(log/merge-config! {:app-name (cfg/client-id)})

; Set Zulu timestamp format for all loggers
(log/merge-config! {:timestamp-opts {:pattern "[yyyy-MM-dd'T'HH:mm:ss.SSS'Z']",
                                      :locale :jvm-default, :timezone :utc}})

; Disable the default stdout logger from timbre
(log/merge-config! {:appenders {:println nil}})

; Create bunyan loggers for stdout and a log file
(log/merge-config! {:appenders {:bunyan (bunyan-appender)}})

#_(log/merge-config! {:appenders {:bunyan-spit (bunyan-spit-appender
                                                 (str "./logs/" (cfg/client-id) ".log"))}})
#_(log/merge-config! {:appenders {:bunyan-rolling (bunyan-rolling-appender
                                                    (str "./logs/" (cfg/client-id) "-rolling.log"))}})

(log/merge-config! {:appenders {:bunyan-rotor (bunyan-rotor-appender
                                                {:path (str "./logs/" (cfg/client-id) ".log")
                                                 :max-size (cfg/max-log-size-bytes)
                                                 :backlog (cfg/max-log-files)})}})
#_(print log/*config*)
