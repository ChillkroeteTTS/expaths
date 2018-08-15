(ns tourme.logging
  (:require [tourme.config :as config]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]))

(def fb (.-default (js/require "react-native-firebase")))
(def cl (.crashlytics fb))

(def logging-on? {:pos  config/debug-position?
                  :fb   config/debug-firebase?
                  :fs   config/debug-firestore?
                  :perm config/debug-permissions?})

(def logs (atom []))
(def last-log-check (atom nil))
(def interval-in-s 60)

(defn logs-snapshot [] @logs)

(defn dprn [key & args]
  (let [msg              (apply str (interleave (repeat " ") args))
        log-msg          (str (tf/unparse (:time tf/formatters) (t/now)) " " key " " msg)
        logging-enabled? (or (not (contains? logging-on? key)) (get logging-on? key))
        log-prod-fn      (fn [msg] (when (not goog.DEBUG) (.log cl msg)))]
    (when (and logging-enabled? (not tourme.config/INTERNALTEST))
      (print log-msg)
      (swap! logs (fn [o] (conj o log-msg)))
      (log-prod-fn log-msg))
    (when tourme.config/INTERNALTEST
      (swap! logs (fn [o] (conj o log-msg)))
      (log-prod-fn log-msg))))


(defn setup-logging! []
  (when tourme.config/INTERNALTEST
    (r/track!
      (fn []
        (let [now @(rf/subscribe [:tick])]
          (when (>= (t/in-seconds (t/interval (or @last-log-check (t/epoch)) now)) interval-in-s)
            (reset! last-log-check now)
            (dprn :log "Estimated log size "
                  (str (/ (* 16 (reduce (fn [agg l] (+ agg (count l)))
                                        0
                                        @logs))
                          8 1024) "kb"))))))))

(rf/reg-fx
  :log/send-bug-report
  (fn [{:keys [msg]}]
    ()))