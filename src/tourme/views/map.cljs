(ns tourme.views.map
  (:require [tourme.views.views :as v]
            [tourme.style :as styl]
            [tourme.helper :as h]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [tourme.db :as db]
            [tourme.debug-data :as dd]
            [cljs-time.core :as t]
            [tourme.logging :as l]))

(def ReactNative (js/require "react-native"))
(def ReactNativeMaps (js/require "react-native-maps"))
(def ReactNativeMapsDirections (js/require "react-native-maps-directions"))

(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def map-view (r/adapt-react-class (.-default ReactNativeMaps)))
(def marker (r/adapt-react-class (.-Marker ReactNativeMaps)))
(def polyline (r/adapt-react-class (.-Polyline ReactNativeMaps)))
(def callout (r/adapt-react-class (.-Callout ReactNativeMaps)))
(def directions (r/adapt-react-class (.-default ReactNativeMapsDirections)))

(def api-key "AIzaSyBKWgGuraNlQu39iOUGMBwlwswvrEOCNuc")

(defn update-loc-position [i loc e]
  (rf/dispatch [:tour-assoc-location i
                (assoc loc :position (:coordinate (js->clj (.-nativeEvent e) :keywordize-keys true)))]))


(defn location-marker [name type position on-marker-drag draggable? start? end? on-press]
  (let [marker-style (styl/get-image type :map)]
    [marker {:draggable  draggable?
             :title      name
             :coordinate position
             :image      ((keyword (str "img" (cond
                                                start? "-start"
                                                end? "-end"
                                                :else ""))) marker-style)
             :anchor     (get-in marker-style [:style :anchor])
             :onDragEnd  on-marker-drag
             :on-press   on-press}]))

(defn loc-button-border-hack-fn [changing-region? loc-button-border-hack timeout]
  "super hacky forcing the component to repaint to show my location button"
  (js/setTimeout (fn []
                   (if @changing-region?
                     (loc-button-border-hack-fn changing-region? loc-button-border-hack 500)
                     (reset! loc-button-border-hack 0))) timeout))

(defn stateful-map [{:keys [tour position draggable-markers? focused-region fit mfte-watch
                            mate-watch map-ref on-load on-marker-drag custom-markers
                            shows-users-location? on-region-change custom-initial-pos height
                            routing? directions-start-location hidden-indices colorize-markers
                            show-first-location? on-map-press on-marker-press changing-region?
                            region-changed waypoints]}]
  (let [region-a                  (atom (merge custom-initial-pos
                                               {:latitudeDelta  0.0322
                                                :longitudeDelta 0.0021}))
        loc-button-border-hack    (r/atom 0.2)
        loc-button-border-hack-fn (partial loc-button-border-hack-fn
                                           changing-region?
                                           loc-button-border-hack
                                           (if directions-start-location 1000 500))]
    (when on-region-change (on-region-change @region-a))
    (r/create-class
      {:reagent-render
       (fn [{:keys [tour position draggable-markers? focused-region fit mfte-watch
                    mate-watch map-ref on-load on-marker-drag custom-markers
                    shows-users-location? on-region-change custom-initial-pos height routing?
                    directions-start-location hidden-indices colorize-markers
                    show-first-location? on-map-press on-marker-press changing-region?
                    region-changed waypoints]}]
         (let [directionable-tour? (and tour (= (:id @waypoints) (:id tour)) (> (count (:locations tour)) 1))]
           [view {:style {:border-width @loc-button-border-hack :min-width "100%"}}
            (h/concatv
              [map-view {:style                     {:height "100%" :min-width "100%"}
                         :provider                  "google"
                         :region                    @region-a
                         :showsUserLocation         (if (not (nil? shows-users-location?))
                                                      shows-users-location? true)
                         :on-region-change          (fn [region]
                                                      (reset! changing-region? true)
                                                      (reset! region-a
                                                              (js->clj region :keywordize-keys true))
                                                      (when on-region-change
                                                        (on-region-change region)))
                         :on-press                  on-map-press
                         :on-region-change-complete (fn []
                                                      (reset! changing-region? false)
                                                      (reset! region-changed (t/now)))
                         :showsMyLocationButton     true
                         :move-on-marker-press      false
                         :onLayout                  (fn [] (when on-load (js/setTimeout on-load 700)))
                         :ref                       (fn [ref] (reset! map-ref ref))}]
              (if custom-markers
                custom-markers
                (map-indexed (fn [i {:keys [name location-type position] :as loc}]
                               (when (not (contains? hidden-indices i))
                                 [location-marker
                                  name location-type position (fn [e]
                                                                (update-loc-position i loc e)
                                                                (when on-marker-drag (on-marker-drag)))
                                  draggable-markers?
                                  (if-let [start? (:start? colorize-markers)] start? (= i 0))
                                  (and (= i (- (count (:locations tour)) 1))
                                       (if (some? (:end? colorize-markers))
                                         (:end? colorize-markers)
                                         true))
                                  (fn [] (when on-marker-press (on-marker-press i loc)))]))
                             (:locations tour)))
              (when directionable-tour? ;; ADD invisible marker so that fit to markers takes current pos into account
                [[polyline {:coordinates  (:wps @waypoints)
                            :stroke-width 3
                            :stroke-color styl/sec}]])
              (when directions-start-location
                [[marker {:draggable  false
                          :title      ""
                          :coordinate directions-start-location}
                  [view {:style {:width 1 :height 1 :background-color "rgba(0,0,0,0)"}}]]]))]))
       :component-will-mount
       loc-button-border-hack-fn
       :component-will-unmount
       (fn []
         (when mate-watch (rf/dispatch [:deregister-watch :map/mate]))
         (when mfte-watch (rf/dispatch [:deregister-watch :map/mfte])))
       })))

(defn message [content style]
  [view {:style (merge {:elevation     5 :width "100%" :height 30 :background-color styl/prim
                        :border-width  0.5 :border-color styl/sec :padding 5 :border-radius 2
                        :margin-bottom 5} style)}
   [text {:style (merge styl/font-text {:font-size 12})}
    content]])

(defn messages [routing?]
  (let [routing-trigger    (r/atom false)
        position-trigger   (r/atom false)
        messages           {routing-trigger  "fetching directions..."
                            position-trigger "missing gps signal..."}
        delayed-trigger-fn (fn [val-a trigger bool-fn]
                             (if (bool-fn @val-a)
                               (js/setTimeout (fn [] (reset! trigger (bool-fn @val-a)))
                                              500)
                               (reset! trigger (bool-fn @val-a))))]
    (r/track! (partial delayed-trigger-fn routing? routing-trigger identity))
    (r/track! (partial delayed-trigger-fn (rf/subscribe [:pos-status]) position-trigger
                       #(not= % :ok)))
    (fn []
      [view {:name           "messages" :style {:position :absolute :top 10 :right 10}
             :pointer-events :none}
       (doall
         (for [[trigger msg] messages]
           (when @trigger
             ^{:key trigger}
             [message msg nil])))])))

(defn try-draw-directions [tour waypoints changing-region?]
  "Waits until map animations are done to prevent stuttering"
  (let [directions @(rf/subscribe [:directions (:id tour)])]
    (if (not @changing-region?)
      (do
        (l/dprn :map :reset-waypoints (count directions))
        (reset! waypoints
                {:id  (:id tour)
                 :wps (mapv (fn [[lat long]] {:longitude long :latitude lat}) directions)}))
      (js/setTimeout (partial try-draw-directions tour waypoints changing-region?)
                     500))))

(defn animate-to-pos [map-ref pos]
  (when (and pos @map-ref)
    (.animateToCoordinate @map-ref (clj->js pos))))

(defn location-map [{:keys [draggable-markers? mfte-watch mate-watch tour on-load
                            custom-initial-pos height map-ref] :as props}]
  "mfte-watch - map fit to element watch atom;;; mate-watch - map animate to watch"
  (let [focused-region    (rf/subscribe [:map-focus])
        position          (rf/subscribe [:position])
        tracking          (atom [])
        map-ref           (or map-ref (r/atom nil))
        fit-to-elements   (fn [] (when @map-ref

                                   (.fitToElements @map-ref true)))
        changing-region?  (r/atom false)
        region-changed    (r/atom nil)
        waypoints         (r/atom nil)
        register-watches  (fn []
                            (when mfte-watch
                              (rf/dispatch [:register-watch :map/mfte
                                            (fn [] (when @mfte-watch (fit-to-elements)))]))
                            (when mate-watch
                              (rf/dispatch [:register-watch :map/mate
                                            (fn [] (animate-to-pos map-ref @mate-watch))])))
        routing?          (r/atom false)]
    (r/create-class
      {:reagent-render
       (fn [{:keys [draggable-markers? mfte-watch mate-watch tour on-load
                    custom-initial-pos height on-region-change] :as props}]
         (if @position
           (let [pos (or custom-initial-pos @position)]
             (when (and tour (>= (count (:locations tour)) 2))
               (reset! routing? true)
               (rf/dispatch [:request-directions tour
                             (fn []
                               (reset! routing? false)
                               (try-draw-directions tour waypoints changing-region?))
                             (fn [failure] (reset! routing? false) (js/alert "Fetching directions failed."))]))
             [view {:style {:height (when height height) :flex (if height 0 1)}}
              [stateful-map (merge props {:custom-initial-pos pos
                                          :map-ref            map-ref
                                          :on-load            register-watches
                                          :on-marker-drag     (fn [] (animate-to-pos map-ref @mate-watch))
                                          :routing?           routing?
                                          :changing-region?   changing-region?
                                          :region-changed     region-changed
                                          :waypoints          waypoints})]
              [messages routing?]])
           [view {:style
                  {:flex            (if height 0 1)
                   :height          (when height height)
                   :justify-content :center
                   :align-items     :center}}
            [v/no-gps-message]]))})))
