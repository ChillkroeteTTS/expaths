(ns tourme.android.navigation
  (:require [reagent.core :as r]
            [tourme.config :as config]
            [cljs.core.async :refer [put! close! chan <!]]
            [tourme.logging :as l]
            [tourme.components :as c])
  (:require-macros [cljs.core.async :refer [go-loop]]))


(def ReactNavigation (js/require "react-navigation"))

(def listeners (atom []))
(def listening-channels (atom {}))

(defonce top-level-navigator (atom nil))

(defn register-nav-listener [key ch]
  (l/dprn :nav/reg :listener key)
  (swap! listening-channels (fn [o] (assoc o key ch))))

(defn listening-for-screen-switch! [screen on-focus on-blur]
  (let [ch (chan)]
    (swap! listeners
           (fn [o]
             (conj o
                   (go-loop []
                     (let [{:keys [type to from]} (<! ch)]
                       (when (= type :screen-switch)
                         (cond
                           (= from screen) (on-blur)
                           (= to screen) (on-focus))
                         (recur)))))))
    ch))

(defn deregister-nav-listener [key]
  (let [listener (get @listening-channels key)]
    (l/dprn :nav/dereg :listener key)
    (if listener
      (close! listener)
      (l/dprn :nav/dereg :not-existent))
    (swap! listening-channels (fn [o] (dissoc o key)))))

(defn current-route-name [state]
  (let [index (.-index state)]
    (when state
      (when-let [route (aget (.-routes state) index)]
        (.-routeName route)))))

(defn send-to-listeners! [event]
  (l/dprn :nav/send-event event)
  (run! (fn [ch] (put! ch event))
        (vals @listening-channels)))

(defn setup! []
  (run! close! (vals @listening-channels))
  (run! close! (vals @listeners))
  (reset! listening-channels {})
  (reset! listeners []))

(defn on-navigation-change [prev-state curr-state]
  (let [prev-screen (current-route-name prev-state)
        curr-screen (current-route-name curr-state)]
    (when (not= prev-screen curr-screen)
      (send-to-listeners! {:type :screen-switch
                           :from prev-screen
                           :to   curr-screen}))))

(defn stack-navigator [initial-route navigation-map]
  (let [navigator ((.-StackNavigator ReactNavigation)
                    (clj->js navigation-map)
                    (clj->js {:initialRouteName initial-route
                              :headerMode       "screen"}))]
    navigator))

(defn react-stack-navigator [initial-route navigation-map]
  (r/adapt-react-class (stack-navigator initial-route navigation-map)))

(defn main-navigator [navigation-map]
  (let [initial-route (if goog.DEBUG
                        (condp = config/debug-db-set
                          :follow-tour "FollowTour"
                          :edit-tour "EditTour"
                          :add-tour "EditTour"
                          :add-location "AddLocation"
                          "Home")
                        "Home")]
    (send-to-listeners! {:event :switch-screen :from nil :to initial-route})
    (react-stack-navigator initial-route navigation-map)))

(defn- nav
  ([nav-obj screen]
   (nav nav-obj screen nil))
  ([nav-obj screen params]
   (let [navigate-fn (:navigate nav-obj)]
     (when-not (if params
                 (navigate-fn screen (clj->js params))
                 (navigate-fn screen))
       (prn "Navigation to " screen " failed. Does it really exist?")))))
(defn navigate
  ([props screen]
    (navigate props screen nil))
  ([props screen params]
   (.dismiss c/Keyboard)
   (nav (js->clj (:navigation props)
                 :keywordize-keys true) screen params)))

(defn navigate! [screen params]
  (nav (js->clj (.-_navigation @top-level-navigator)
            :keywordize-keys true)
       screen params))

(defn pop [props n]
  (when (nil? props) (ex-info "Passed empty props to navigation." nil))
  (.dismiss c/Keyboard)
  (let [navigation-obj (js->clj (:navigation props)
                                :keywordize-keys true)]
    ((:pop navigation-obj))))

(defn pop! [n]
  ((:pop (js->clj (.-_navigation @top-level-navigator) :keywordize-keys true))))

(defn get-param [props key]
  (let [navigation-obj (:navigation props)]
    (.getParam navigation-obj (name key) nil)))