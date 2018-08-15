(ns tourme.views.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [tourme.positioning :as p]
            [tourme.helper :as h]
            [tourme.style :as sty]
            [tourme.style :as styl]
            [tourme.android.system :as s]
            [tourme.components :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; BUTTONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn +-button [icon-name icon-size on-press]
  (let [diam 50
        gap  30]
    [c/touchable-highlight {:style    {:border-radius   30 :background-color styl/sec
                                       :height          diam :width diam
                                       :justify-content :center :align-items :center :elevation 2}
                            :on-press on-press :underlay-color styl/sec-l}
     [c/material-icon {:name icon-name :size icon-size :color :white}]]))

(defn +-button-small [icon-name icon-size on-press]
  (let [diam 28]
    [c/touchable-highlight {:style      {:border-radius   diam :background-color styl/sec
                                         :height          diam :width diam
                                         :justify-content :center :align-items :center}
                            :on-press   on-press
                            :background ((.. c/ReactNative -TouchableNativeFeedback -Ripple) "#000000" true)}
     [c/material-icon {:name icon-name :size icon-size :color :white}]]))

(defn bottom-button [{:keys [on-press title]}]
  [c/touchable-highlight {:style          {:position :absolute :bottom 0 :width "100%"}
                          :underlay-color styl/sec-l
                          :on-press       on-press}
   [c/view {:style {:width            "100%" :height 50
                    :background-color styl/sec :align-items :center
                    :justify-content  :center}}
    [c/text {:style (merge styl/font-button {:color :white})} title]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; RANDOM ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filler []
  [c/view {:style {:flex 1}}])

(defn property [icon-name caption]
  [c/view {:style {:flex-direction :column :justify-content :center :align-items :center}}
   [c/material-icon {:name icon-name :color styl/sec :size 25}]
   [c/text {:style (merge styl/font-text {:font-size 10 :width "100%" :text-align :center})}
    caption]])

(defn pulsating-dots [{:keys [style]}]
  (let [opacity (c/AnimatedValue. 0.0)]
    (->> (.sequence c/Animated (clj->js [(.timing c/Animated opacity #js {:toValue 1.0 :duration 2000})
                                         (.timing c/Animated opacity #js {:toValue 0.0 :duration 2000})]))
         (.loop c/Animated)
         (.start))
    (fn []
      [c/animated-text {:style (merge styl/message-font
                                      {:font-size 17 :font-weight "bold"
                                       :opacity   opacity}
                                      style)}
       "..."])))

(defn message [message]
  (fn []
    [c/view (merge styl/absolute-filling-object {:align-items :center :justify-content :center})
     [c/view {:style {:position         :absolute :width 200 :padding-vertical 10 :padding-horizontal 30
                      :background-color styl/prim :bottom 130 :border-radius 20 :border-width 1
                      :border-color     styl/sec :align-items :center}}
      [c/text {:style styl/font-explanation}
       message]]]))

(defn loading-screen-overlay [{:keys [reasons]}]
  [c/view {:style (merge styl/absolute-filling-object
                         {:background-color "rgba(0,0,0,0.3)"
                          :align-items      :center :justify-content :center})}
   [c/view {:style {:width   200 :background-color styl/prim
                    :padding 15 :align-items :center :border-radius 15}}
    [c/text {:style (merge styl/font-heading1 {:margin-bottom 7})}
     "calling home"]
    (when (or tourme.config/INTERNALTEST goog.DEBUG)
      (for [reason reasons]
        ^{:key reason}
        [c/text {:style (merge styl/font-text {:color styl/sec})}
         reason]))
    [pulsating-dots {:style styl/font-heading1}]]])

(defn no-gps-message []
  [c/view {:style {:flex-direction "row"}}
   [c/text {:style (merge styl/message-font {:font-size 17 :font-weight "bold"})}
    "Waiting for GPS position"]
   [pulsating-dots]])

(defn rating-star-bar [{:keys [container-style interactive? rating-a] :as props} rating]
  (let [rating-a (if rating-a rating-a (r/atom rating))]
    (fn [{:keys [container-style interactive?] :as props} rating]
      (let [props        (dissoc props :container-style :interactive?)
            interactive? (if (nil? interactive?) true interactive?)]
        (into [c/view {:name           "rating-container"
                       :pointer-events (if interactive? :auto :none)
                       :style          (merge {:flex-direction :row :justify-content :center
                                               :align-items    :center}
                                              container-style)}]
              (map (fn [i] (let [filled? (<= i (int (+ 0.5 (:score @rating-a))))]
                             [c/touchable-opacity {:on-press       (partial swap!
                                                                            rating-a
                                                                            (fn [o] (assoc o :score i)))
                                                   :active-opacity 1 :disabled (if interactive? false true)}
                              [c/material-icon (merge {:name  "star"
                                                       :color (if filled? "rgba(247,232,28,1)"
                                                                          styl/color-disabled)}
                                                      props)]]))
                   (range 1 6)))))))

(defn modal-button [caption on-press]
  [c/touchable-highlight {:style          {:background-color styl/sec :flex 1 :align-items :center
                                           :justify-content  :center}
                          :underlay-color styl/sec-l
                          :on-press       on-press}
   [c/text {:style (merge styl/font-button {:font-size 12})}
    caption]])

(defn discard-changes-modal [show-modal? on-ok]
  (let [hide-modal! (partial reset! show-modal? false)]
    [c/modal {:visible              @show-modal?
              :on-request-close     hide-modal!
              :transparent          true :animation-type :fade
              :hardware-accelerated true}
     [c/view {:style (merge styl/absolute-filling-object
                            {:background-color "#00000030" :justify-content :center
                             :align-items      :center})}
      [c/view {:style {:width         200 :background-color styl/prim :opacity 1
                       :border-radius 0 :padding-top 20 :elevation 5}}
       [c/view {:style {:padding-left 20 :padding-right 20}}
        [c/text {:style (merge styl/message-font {:text-align :center :margin-bottom 20
                                                  :font-size  17})}
         "Discard changes?"]
        [c/text {:style (merge styl/message-font {:text-align :center :font-size 10})}
         "If you leave the screen, all unsaved changes will be lost."]]
       [c/view {:style {:flex-direction :row :height 30 :margin-top 20}}
        [modal-button "stay" hide-modal!]
        [c/view {:style {:width 1 :background-color styl/prim}}]
        [modal-button "leave" on-ok]]]]]))

(defn link [{:keys [color clicked-color style on-press caption animation-config]}]
  (let [color (r/atom color)
        props {:on-press (fn [] (reset! color clicked-color) (on-press)) :style (merge (or style {}) {:color @color})}]
    (if animation-config
      [c/view {:style (:container-style animation-config)}
       [c/animated-text (assoc props :style (merge (:style props) {:position :absolute
                                                                   :opacity  (.interpolate (:value animation-config)
                                                                                           (clj->js {:inputRange  [0 1]
                                                                                                     :outputRange [1 0]}))}))
        caption]
       [c/animated-text (assoc props :style (merge (:style props) {:position :absolute
                                                                   :opacity  (:value animation-config)}))
        (:caption2 animation-config)]]
      [c/text props caption])))

(defn explanation [{:keys [caption up]}]
  [c/view {:style {:align-items         :center :justify-content :space-between :width "100%"
                   :border-bottom-color styl/sec :border-bottom-width 1
                   :padding-vertical    4 :flex-direction :row}}
   (if up
     [c/touchable-opacity {:on-press (:on-press up)}
      [c/view {:style {:width 100 :margin-left 5 :height 13}}
       [c/animated-view {:style {:opacity (.interpolate (:animation-value up)
                                                        (clj->js {:inputRange  [0 1]
                                                                  :outputRange [1 0]}))}}
        (when up [c/material-icon {:name "keyboard-arrow-up" :size 15 :color styl/sec}])]
       [c/animated-view {:style {:opacity  (:animation-value up)
                                 :position :absolute}}
        (when up [c/material-icon {:name "keyboard-arrow-down" :size 15 :color styl/sec}])]]]
     [c/view {:name "dummy" :style {:width 100}}])
   [c/text {:style (merge styl/font-explanation {})}
    caption]
   [c/view {:name "dummy" :style {:width 100}}]])

(defn arrow [{:keys [color size transform style]}]
  [c/view {:style (merge {:width               0 :height 0 :background-color :transparent :border-style :solid
                          :border-left-width   size :border-right-width size :border-bottom-width size
                          :border-left-color   :transparent :border-right-color :transparent
                          :border-bottom-color color :transform (concat [{:scaleY 1}]
                                                                        (or transform []))}
                         style)}])

(defn open-link [url]
  (let [not-supported-action (fn []
                               (js/alert
                                 (str "Expaths couldn't open your browser. Please visit "
                                      url " manually.")))]
    (.then (.canOpenURL c/Linking url)
           (fn [supported?]
             (if supported?
               (.openURL c/Linking url)
               (not-supported-action)))
           not-supported-action)))

(defn toggleable-element [check-a text url]
  (let [text-style (merge styl/font-text {:font-size 13})]
    [c/view {:style {:flex-direction :row :align-items :center}}
     [c/text {:style text-style}
      (str "I accept Expath's ")]
     [link {:on-press      (partial open-link url) :color styl/sec
              :clicked-color styl/sec-d :caption text
              :style         text-style}]
     [c/checkbox {:value @check-a :on-value-change (fn [] (swap! check-a not))}]]))