(ns tourme.effect-handlers
  (:require
    [re-frame.core :refer [reg-event-db after reg-event-fx reg-fx]]
    [re-frame.core :as rf]
    [tourme.config :as config]
    [tourme.logging :as l]
    [cljs.core.async :refer [put! chan <! >! timeout close! go go-loop]]))

(defn dprn [& args] (apply (partial l/dprn :fb) args))
;; HTTP

#_{:method          :get
   :uri             (str "https://maps.googleapis.com/maps/api/distancematrix/json?units=metric&mode=walking"
                         "&origins=" origins-str
                         "&destinations=" destinations-str
                         "&key=AIzaSyBKWgGuraNlQu39iOUGMBwlwswvrEOCNuc")
   :timeout         1000
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      [:http-success :calc-length id]
   :on-failure      [:http-failure :calc-length]}

(defn http-request [uri method on-success on-failure]
  (.then (.then (js/fetch uri (clj->js {:method method}))
                (fn [resp] (.then (.json resp)
                                  (fn [json] (on-success (js->clj json :keywordize-keys true)))
                                  on-failure))
                on-failure)))

(defn http-effect [{:keys [method uri on-success on-failure :as request]}]
  (http-request uri method
                (fn [json] (rf/dispatch (conj on-success
                                              (js->clj json
                                                       :keywordize-keys true))))
                (fn [] (rf/dispatch on-failure))))

(rf/reg-fx :http-xhrio http-effect)