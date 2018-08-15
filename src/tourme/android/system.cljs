(ns tourme.android.system
  (:require [re-frame.core :as rf]
            [cljs.reader :as cljsr]
            [tourme.config :as c]
            [cljs.core.async :refer [put! chan <! >! timeout close! go]]
            [tourme.logging :as l]
            [reagent.core :as r]))

(def ReactNativePermissions (js/require "react-native-permissions"))
(def Geolocation (.-default (js/require "react-native-geolocation-service")))
(def Permissions (.-default ReactNativePermissions))

(def perm-location "location")

(defn dprn [& args] (apply (partial l/dprn :perm) args))
(defn dpprn [& args] (apply (partial l/dprn :pos) args))

(defn- request-perm! [ch perm]
  (.then (.request Permissions perm)
         (fn [s]
           (cond
             (= s "authorized") (put! ch {:type :permission :msg :authorized})
             (= s "restricted") (put! ch {:type :permission :msg :restricted})
             (= s "denied") (put! ch {:type :permission :msg :denied})
             (= s "undetermined") (dprn "Something went wrong, perm status for " perm " is still undetermined.")))
         dprn))

(defn request-permission [ch perm]
  (.then (.check Permissions perm)
         (fn [s]
           (dprn perm " is: " s)
           (cond
             (= s "authorized") (put! ch {:type :permission :msg :authorized})
             (= s "restricted") (put! ch {:type :permission :msg :restricted})
             (or (= s "undetermined")
                 (= s "denied")) (do (dprn "Try requesting it...")
                                     (request-perm! ch perm))))
         dprn))

(defn wait-for-permission
  ([permission cb-granted] (wait-for-permission permission cb-granted identity))
  ([permission cb-granted cb-denied]
   (go
     (let [ch (chan)]
       (request-permission ch permission)
       (let [{s :msg} (<! ch)]
         (cond
           (= s :authorized) (cb-granted)
           (or (= s :restricted)
               (= s :denied)) (cb-denied)))))))

(defn wire-position-to-rf-state! []
  (let [default-interval          30
        position-query-interval-s (atom default-interval)
        cnt                       (atom @position-query-interval-s)]
    (rf/dispatch [:register-watch :position-wiring
                  (fn []
                    (let [_            @(rf/subscribe [:tick])
                          p-state      @(rf/subscribe [:pos-status])
                          update-location-fn
                                       (fn [e]
                                         (rf/dispatch [:pos-status :ok])
                                         (reset! position-query-interval-s default-interval)
                                         (dpprn "Received new position, update state " (js->clj (.-coords e)))
                                         (rf/dispatch [:update-position (select-keys (:coords (js->clj e :keywordize-keys true))
                                                                                     [:longitude :latitude])]))
                          log-error-fn (fn [e]
                                         (dpprn "get current location error error: " (.-code e) " " (.-message e))
                                         (rf/dispatch [:pos-status :disabled])
                                         (reset! position-query-interval-s 8))]
                      (swap! cnt inc)
                      (when (<= @position-query-interval-s @cnt)
                        (reset! cnt 0)
                        (.getCurrentPosition Geolocation
                                             update-location-fn
                                             log-error-fn
                                             (clj->js {:timeout            25000
                                                       :enableHighAccuracy true
                                                       :showLocationDialog true})))))])))

(defn set-up-location-hook []
  (wait-for-permission perm-location
                       wire-position-to-rf-state!
                       #(dpprn "No location permission")))