(ns tourme.screens.follow-tour
  (:require [tourme.views.map :as m]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [tourme.views.views :as v]
            [tourme.style :as styl]
            [tourme.android.navigation :as n]
            [tourme.components :as c]
            [tourme.views.card :as card]
            [tourme.helper :as h]
            [cljs-time.core :as t]
            [goog.string :as gstring]
            [goog.string.format]
            [tourme.debug-data :as dd]))

(def ReactNative (js/require "react-native"))
(def ReactNativeMaterialIcons (js/require "react-native-vector-icons/MaterialIcons"))

(def view (r/adapt-react-class (.-View ReactNative)))
(def modal (r/adapt-react-class (.-Modal ReactNative)))
(def button (r/adapt-react-class (.-Button ReactNative)))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def touchable-highlight (r/adapt-react-class (.-TouchableHighlight ReactNative)))
(def material-icon (r/adapt-react-class (.-default ReactNativeMaterialIcons)))

(defn tour-description-bar []
  (let [tour (rf/subscribe [:followed-tour])]
    (fn []
      [view {:style {:flex-direction :row :background-color styl/prim :align-items :center
                     :width          "100%" :height 50 :padding-left 10 :elevation 8}}
       [text {:style styl/font-heading-top-bar}
        (:name @tour)]
       [view {:style {:flex 1}}]
       [v/rating-star-bar {:size 20 :container-style {:margin-right 15} :interactive? false}
        (:rating @tour)]])))

(defn rate-tour-modal [show-modal? modal-rating-a tour]
  [modal {:visible              @show-modal?
          :on-request-close     (partial reset! show-modal? false)
          :transparent          true :animation-type :fade
          :hardware-accelerated true}
   [view {:style (merge styl/absolute-filling-object
                        {:background-color "#00000030" :justify-content :center
                         :align-items      :center})}
    [view {:style {:width         200 :background-color styl/prim :opacity 1
                   :border-radius 20 :padding 20}}
     [text {:style (merge styl/message-font {:text-align :center :margin-bottom 20
                                             :font-size  17})}
      "Please rate your trip"]
     [v/rating-star-bar {:size     32 :container-style {:margin-bottom 20}
                         :rating-a modal-rating-a} nil]
     [button {:title "Rate" :on-press (fn []
                                        ;;super hacky, for some reason modal won't disappear without.
                                        ;; Possible reason: closing modal and opening another one causes problems
                                        (js/setTimeout #(reset! show-modal? false) 300)
                                        (rf/dispatch [:rate-tour @tour @modal-rating-a
                                                      (partial n/pop! 1)]))
              :color styl/sec :disabled (= (:score @modal-rating-a) 0)}]]]])

(defn center [p s]
  (- (/ p 2) (/ s 2)))

(defn place-tile [location on-location-click first? last?]
  (let [is-marker? (= :marker (:location-type location))
        tile-h     (if is-marker? 40 80)
        bubble-dia 13
        g-w        60]
    (fn []
      (let [description (:description location)]
        [c/touchable-opacity {:name     "Tile" :style {:flex-direction :row :height tile-h :padding-right 10}
                              :on-press on-location-click :active-opacity 1}
         [c/view {:name "graphic" :style {:align-items :center :justify-content :center
                                          :width       g-w}}
          [c/view {:style {:position         :absolute :top (if first? (/ tile-h 2) 0)
                           :bottom           (if last? (/ tile-h 2) 0) :left (center g-w 5) :width 5
                           :background-color styl/prim-d}}]
          (when (not is-marker?)
            [c/view {:style {:width            (* 2.5 bubble-dia) :border-radius (* 2.5 bubble-dia)
                             :background-color styl/sec
                             :height           (* 2.5 bubble-dia) :position :absolute
                             :top              (center tile-h (* 2.5 bubble-dia)) :left (center g-w (* 2.5 bubble-dia))}}])
          [c/view {:style {:width            bubble-dia :border-radius bubble-dia
                           :background-color (if is-marker? styl/sec styl/prim)
                           :height           bubble-dia :position :absolute
                           :top              (center tile-h bubble-dia) :left (center g-w bubble-dia)}}]]
         (when (not is-marker?)
           [c/view {:name "info" :style {:flex 1 :justify-content :center}}
            [c/text {:style (merge styl/font-heading2 {})} (:name location)]
            [c/text {:style (merge styl/font-text {:max-height 55})} description]])]))))

(defn animate-to [last-exp expanded closed-or-expanded]
  (let [target (condp = closed-or-expanded :closed 0.0 :expanded 1.0)]
    (.start (.timing c/Animated expanded #js {:toValue         target :duration 500
                                              :useNativeDriver true})
            #(reset! last-exp target))))

(defn toggle-info-bar [last-exp expanded on-finish]
  [c/view {:style {:flex-direction   :row :align-items :center :height 40
                   :padding-vertical 5 :padding-horizontal 8}}
   [v/link {:color            styl/light-gray :clicked-color styl/light-gray
            :animation-config {:value           expanded
                               :caption2        "Less Info"
                               :container-style {:height 20}}
            :caption          "More Info"
            :on-press         (fn []
                                (let [target-pos (if (>= @last-exp 0.2) :closed :expanded)]
                                  (animate-to last-exp expanded target-pos)))}]
   [v/filler]
   [c/view {:style {:width 70}}
    [v/modal-button "Finished" on-finish]]])

(defn tour-info [tour on-location-click on-finish]
  (let [last-exp (r/atom 0.0)
        expanded (c/AnimatedValue. @last-exp)]
    (fn [tour on-location-click on-finish]
      [card/card
       {:closed-height   120 :expanded-height 300
        :last-exp        last-exp :expanded expanded
        :handle-style    {:background-color    styl/prim :height 80 :border-bottom-width 1
                          :border-bottom-color "rgba(207,207,207,1)"
                          :padding-horizontal  10 :padding-vertical 5}
        :container-style {:background-color styl/prim :width "100%" :elevation 8}
        :handle-content
                         (lazy-seq
                           [[c/text {:style (merge styl/font-heading-top-bar {:margin-bottom 8})}
                             (:name @tour)]
                            [c/view {:style {:flex-direction :row}}
                             [c/text {:style (merge styl/message-font {:margin-right 8})}
                              (if-let [score (:score (:rating @tour))]
                                (gstring/format "%.1f" score)
                                "-")]
                             [v/rating-star-bar {:size 15 :interactive? false} (:rating @tour)]
                             [c/text {:style (merge styl/message-font {:margin-left 5})}
                              (str "(" (:cnt (:rating @tour)) ")")]]])
        :container-content
                         (lazy-seq
                           [[toggle-info-bar last-exp expanded on-finish]
                            [c/scroll-view {:style {:padding-vertical 5}}
                             [c/text {:style (merge styl/font-text
                                                    {:margin-top         10 :margin-bottom 30 :max-height 70
                                                     :padding-horizontal 10})} (if (empty? (:description @tour))
                                                                                 "No description provided."
                                                                                 (:description @tour))]
                             [c/view {:style {:margin-bottom 20}}
                              (doall (for [[i location] (map-indexed vector (:locations @tour))]
                                       ^{:key i}
                                       [place-tile location (fn []
                                                              (on-location-click i)
                                                              (animate-to last-exp expanded :closed))
                                        (= i 0) (= i (- (count (:locations @tour)) 1))]))]]])}])))

(defn follow-tour-screen [props]
  (let [tour                   (rf/subscribe [:followed-tour])
        show-modal?            (r/atom false)
        modal-rating-a         (r/atom {:score 0.0 :cnt 0})
        map-ref                (atom nil)
        enter-screen-timestamp (t/now)
        leave-or-rate          (fn [] ;;allow user to quit navigation only the first x seconds
                                 (if (<= (t/in-seconds (t/interval enter-screen-timestamp (t/now)))
                                         60)
                                   (n/pop! 1)
                                   (swap! show-modal? not)))]
    (rf/dispatch [:register-watch :followtour/init-mfte
                  (fn []
                    (when @map-ref
                      (js/setTimeout #(.fitToElements @map-ref true) 500))
                    (rf/dispatch [:deregister-watch :followtour/init-mfte]))])
    (r/create-class
      {:reagent-render
       (fn []
         [view
          [view {:style {:flex-direction :column :align-items :center :justify-content :flex-start
                         :height         "100%"}}
           #_[tour-description-bar]
           [m/location-map {:draggable-markers? false
                            :mfte-watch         tour
                            :tour               @tour
                            :map-ref            map-ref
                            :height             (- (.-height (.get (.-Dimensions c/ReactNative) "window"))
                                                   144)}]
           [c/view {:style {:position        :absolute :bottom 140 :background-color :white :right 10
                            :flex-direction  :row :width 110 :height 50
                            :justify-content :space-evenly :align-items :center}}
            [v/property "directions" (h/distance->str (:length @tour))]
            [v/property "access-time" (h/duration->str (:duration @tour))]]

           #_[v/bottom-button {:on-press (fn [] (reset! show-modal? true))
                               :title    "Finish!"}]]
          [tour-info tour (fn [i] (m/animate-to-pos map-ref (:position (nth (:locations @tour) i))))
           (fn [] (reset! show-modal? true))]
          [rate-tour-modal show-modal? modal-rating-a tour]])
       :component-did-mount
       (fn []
         (.addEventListener c/BackHandler "hardwareBackPress" leave-or-rate))
       :component-will-unmount
       (fn []
         (.removeEventListener c/BackHandler "hardwareBackPress" leave-or-rate))})))