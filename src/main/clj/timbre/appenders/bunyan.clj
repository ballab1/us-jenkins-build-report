(ns timbre.appenders.bunyan
  "Create a Node.js bunyan compatible logger logging in JSON format for data mining by e.g. Elastic Search.
  To view generated logs in human friendly format use the the bunyan CLI utility.
  Simply install with 'npm i -g bunyan'
  Then 'run dev | bunyan' or 'bunyan some-log-file' "
  {:clj-kondo/config
   '{:linters {:unresolved-symbol {:exclude [(timbre.appenders.bunyan [logger])]}}}}
  (:require [cheshire.core :as json]
            [config.env :as cfg]
            [clojure.string :as string]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as tac]
            #_[taoensso.timbre.appenders.3rd-party.rolling :as rol-app]
            [taoensso.timbre.appenders.3rd-party.rotor :as rot-app])
  (:gen-class))

; Intern log level macros as to appear belonging to this name space.
; https://stackoverflow.com/questions/20831029/how-is-it-possible-to-intern-macros-in-clojure
(intern 'timbre.appenders.bunyan (with-meta 'trace {:macro true}) @#'taoensso.timbre/trace)
(intern 'timbre.appenders.bunyan (with-meta 'debug {:macro true}) @#'taoensso.timbre/debug)
(intern 'timbre.appenders.bunyan (with-meta 'info {:macro true}) @#'taoensso.timbre/info)
(intern 'timbre.appenders.bunyan (with-meta 'warn {:macro true}) @#'taoensso.timbre/warn)
(intern 'timbre.appenders.bunyan (with-meta 'error {:macro true}) @#'taoensso.timbre/error)
(intern 'timbre.appenders.bunyan (with-meta 'fatal {:macro true}) @#'taoensso.timbre/fatal)

(def spy #(do (println "DEBUG:" %) %))

(def bunyan-levels
  "Maps a logging level keyword into a bunyan integer level"
  {:trace 10
   :debug 20
   :info 30
   :warn 40
   :error 50
   :fatal 60})

(def bunyan-logger (atom nil))

(defn init-bunyan-logger []
  (reset! bunyan-logger
          (let [currentPID (-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
                               (.getName)
                               #_spy
                               (string/split #"@")
                               (first))
                name (cfg/app-name)]
            (fn
              ([data] (@bunyan-logger nil data))
              ([opts data]
               (let [{:keys [no-stacktrace? _stacktrace-fonts]} opts
                     {:keys [_pid level ?err msg_ ?ns-str ?file hostname_ timestamp_ ?line]} data
                     stack (when-not no-stacktrace? (when-let [err ?err] {:stack (log/stacktrace err opts)}))]
                 #_(println opts)
                 #_(println data)
                 (json/generate-string (merge (:context data)
                                              {:v 0
                                               :name name
                                               :pid currentPID
                                               :hostname (force hostname_)
                                               :time (force timestamp_)
                                               :msg (force msg_)
                                               :level (level bunyan-levels)}
                                              (if (some? ?line) {:src {:file ?file :line ?line :namespace ?ns-str}} {})
                                              (if (some? stack) {:err stack} {})))))))))

(def iso-pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(defn bunyan-appender
  "Returns a Timbre appender which emits bunyan compatible json strings to stdout"
  []
  (assoc (tac/println-appender)
         :timestamp-opts {:pattern iso-pattern}
         :output-fn @bunyan-logger
         :min-level (cfg/timbre-log-level)
         :ns-whitelist (cfg/timbre-ns-whitelist)
         :ns-blacklist (cfg/timbre-ns-blacklist)))

(defn bunyan-spit-appender
  "Returns a Timbre appender which emits bunyan compatible json strings to a log-file"
  [log-file]
  (assoc (tac/spit-appender {:fname log-file})
         :timestamp-opts {:pattern iso-pattern}
         :output-fn @bunyan-logger))

#_(defn bunyan-rolling-appender
    "Returns a Timbre appender which emits bunyan compatible json strings to a daily rolling log-file"
    [log-file]
    (assoc (rol-app/rolling-appender {:path log-file})
           :timestamp-opts {:pattern iso-pattern}
           :output-fn @bunyan-logger))

(defn bunyan-rotor-appender
  "Returns a rotating file appender."
  [& [{:keys [path max-size backlog]
       :or   {max-size (* 1024 1024 5)
              backlog 5}}]]
  (assoc (rot-app/rotor-appender {:path path :max-size max-size :backlog backlog})
         :timestamp-opts {:pattern iso-pattern}
         :output-fn @bunyan-logger
         :min-level (cfg/timbre-log-level)
         :ns-whitelist (cfg/timbre-ns-whitelist)
         :ns-blacklist (cfg/timbre-ns-blacklist)))
;
; Configure timbre bunyan logging
;

(defn init-logger []
  (init-bunyan-logger)

  ; Set Zulu timestamp format for all loggers
  (log/merge-config! {:timestamp-opts {:pattern "[yyyy-MM-dd'T'HH:mm:ss.SSS'Z']",
                                       :locale :jvm-default, :timezone :utc}})

   ; Disable the default stdout logger from timbre
  (log/merge-config! {:appenders {:println nil}})

   ; Create bunyan loggers for stdout and a log file
  (log/merge-config! {:appenders {:bunyan (bunyan-appender)}})

  #_(log/merge-config! {:appenders
                        {:bunyan-spit
                         (bunyan-spit-appender
                          (str (cfg/log-dir) (when-not (string/ends-with? (cfg/log-dir) "/") "/")
                               (cfg/client-id) ".log"))}})

  #_(log/merge-config! {:appenders
                        {:bunyan-rolling
                         (bunyan-rolling-appender
                          (str (cfg/log-dir) (when-not (string/ends-with? (cfg/log-dir) "/") "/")
                               (cfg/client-id) "-rolling.log"))}})

  (log/merge-config! {:appenders
                      {:bunyan-rotor
                       (bunyan-rotor-appender
                        {:path (str (cfg/log-dir) (when-not (string/ends-with? (cfg/log-dir) "/") "/")
                                    (cfg/client-id) ".log")
                         :max-size (cfg/max-log-size-bytes)
                         :backlog (cfg/max-log-files)})}}))

(mount/defstate logger
  :start (init-logger))
