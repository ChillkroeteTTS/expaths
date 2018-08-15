(ns tourme.views.location-cinema
  (:require [reagent.core :as r]
            [tourme.style :as styl]
            [re-frame.core :as rf]
            [tourme.components :as c]
            [tourme.views.views :as v]
            [tourme.android.navigation :as n]))


(def ReactNative (js/require "react-native"))

(def view (r/adapt-react-class (.-View ReactNative)))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def Animated (.-Animated ReactNative))
(def AnimatedValue (.-Value Animated))
(def animated-view (r/adapt-react-class (.-View Animated)))
(def touchable-opacity (r/adapt-react-class (.-TouchableOpacity ReactNative)))

(def loc-sel-height 30)
(def loc-sel-padding 8)
(def loc-sel-icon-margin 4)
(def loc-sel-location-diam 11)
(def loc-sel-separator-diam 5)
(def loc-sel-pos-0-offset (+ (/ loc-sel-location-diam 2) loc-sel-icon-margin))
(def loc-sel-location-gap (+ (* 6 loc-sel-icon-margin) loc-sel-location-diam (* 2 loc-sel-separator-diam)))
(def loc-sel-selection-diam (- loc-sel-height loc-sel-padding))
(def css-loc-sel-centered {:position  :absolute :left "50%" :top 0 :height loc-sel-height
                           :transform [{:translateX (- (/ loc-sel-selection-diam 2))}]})

(defn pos-x-for-selection [selection] (- (* selection loc-sel-location-gap)))

(defn new-selection [no-of-locations initial-touch-x current-selection e]
  (let [pos-x-for-selection (fn [selection] (- (* selection loc-sel-location-gap)))
        final-diff          (- (+ (pos-x-for-selection current-selection)
                                  (- (.-pageX (.-nativeEvent e)) initial-touch-x)))]
    (min (- no-of-locations 1) (max 0 (int (+ (/ final-diff loc-sel-location-gap) 0.5))))))

(defn loc-sel-location-marker [type on-click]
  [touchable-opacity {:style          {:margin-horizontal loc-sel-icon-margin
                                       :width             loc-sel-location-diam :height loc-sel-location-diam
                                       :justify-content   :center :align-items :center}
                      :active-opacity 0.4
                      :on-press       on-click}
   (cond
     (= type :marker) (let [dia loc-sel-location-diam]
                        [view {:style {:width        dia :height dia :border-width 1
                                       :border-color styl/prim :border-radius dia}}])
     (= type :food) [styl/mat-ico {:name  styl/food-ic :size loc-sel-location-diam
                                     :color styl/prim :style {}}]
     (= type :cafe) [styl/mat-ico {:name  styl/cafe-ic :size loc-sel-location-diam
                                     :color styl/prim :style {}}]
     (= type :viewpoint) [styl/mat-ico {:name  styl/viewpoint-ic :size loc-sel-location-diam
                                     :color styl/prim :style {}}]
     (= type :place) [styl/mat-ico {:name  styl/place-ic :size loc-sel-location-diam
                                    :color styl/prim :style {}}])])

(defn loc-sel-separator []
  [view {:style {:width             loc-sel-separator-diam :height loc-sel-separator-diam :background-color styl/prim
                 :margin-horizontal loc-sel-icon-margin
                 :border-width      1 :border-color styl/prim :border-radius loc-sel-separator-diam
                 }}])

(defn loc-sel-cinema []
  (let [position-x        (AnimatedValue. 0.0)
        selection         (rf/subscribe [:selected-location-index])
        initial-touch-x   (atom 0)
        tour              (rf/subscribe [:editable-tour])
        animate-selection (fn [new-selection]
                            (-> (.timing Animated position-x #js {:toValue  (pos-x-for-selection new-selection)
                                                                  :duration 300})
                                (.start)))]
    (fn []
      (animate-selection @selection)
      [c/view {:name                      "outer box" :style {:height           loc-sel-height :width "100%"
                                                              :background-color styl/sec}
               :onStartShouldSetResponder (constantly true) :onMoveShouldSetResponder (constantly true)
               :onResponderGrant          (fn [e]
                                            (reset! initial-touch-x (.-pageX (.-nativeEvent e))))
               :onResponderReject         (fn [])
               :onResponderMove           (fn [e] (.setValue position-x
                                                             (+ (pos-x-for-selection @selection)
                                                                (- (.-pageX (.-nativeEvent e)) @initial-touch-x))))
               :onResponderRelease        (fn [e]
                                            (let [new-selection (new-selection (count (:locations @tour))
                                                                               @initial-touch-x @selection e)]
                                              (rf/dispatch [:tour-select-location new-selection])
                                              (animate-selection new-selection)))}
       (into [c/animated-view {:name  "sliding-box"
                               :style (merge css-loc-sel-centered
                                             {:transform       [{:translateX (- loc-sel-pos-0-offset)}
                                                                {:translateX position-x}]
                                              :flex-direction  "row"
                                              :justify-content :space-around :align-items :center})}]
             (-> (interleave (map-indexed (fn [i {type :location-type}]
                                            [loc-sel-location-marker type (fn []
                                                                            (rf/dispatch [:tour-select-location i])
                                                                            (animate-selection i))]) (:locations @tour))
                             (repeatedly (constantly [loc-sel-separator]))
                             (repeatedly (constantly [loc-sel-separator])))
                 (butlast)
                 (butlast)))])))

(defn action-icon [{:keys [icon on-press]}]
  [c/touchable-opacity {:on-press on-press}
   [c/material-icon {:name icon :size 16 :color styl/prim}]])

(defn action-bubble [{:keys [on-add on-edit on-delete]}]
  [c/view {:style {:height 40 :align-items :center :margin-bottom 3}}
   [c/view {:style {:justify-content :space-between :background-color styl/sec
                    :flex            1 :flex-direction :row :width 140 :border-radius 15
                    :align-items     :center :padding-horizontal 17}}
    [action-icon {:icon "add" :on-press on-add}]
    [action-icon {:icon "edit" :on-press on-edit}]
    [action-icon {:icon "delete-forever" :on-press on-delete}]]
   [v/arrow {:size 5 :color styl/sec :transform [{:rotate "180deg"}]}]])

(defn loc-cinema-with-buttons [{:keys [on-add on-edit on-delete] :as props}]
  (let [tour (rf/subscribe [:editable-tour])]
    (fn []
      (into [c/view {:style {:flex-direction  :column :align-items :center :width "100%"
                             :justify-content :center :position :absolute :bottom 0
                             }}]
            (if (empty? (:locations @tour))
              [[c/view {:style {:margin-top (- 60)}} [v/+-button "add" 23 on-add]]]
              [[action-bubble {:on-add on-add :on-edit on-edit :on-delete on-delete}]
               [loc-sel-cinema]])))))