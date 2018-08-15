(ns tourme.views.card
  (:require [tourme.components :as c]
            [reagent.core :as r]
            [tourme.helper :as h]))

(defn animate-to [last-exp expanded closed-or-expanded]
  (let [target (condp = closed-or-expanded :closed 0.0 :expanded 1.0)]
    (.start (.timing c/Animated expanded #js {:toValue         target :duration 500
                                              :useNativeDriver true})
            #(reset! last-exp target))))

(defn card [{:keys [handle-style container-style closed-height expanded-height last-exp expanded
                    handle-content container-content prevent-expanding?]}]
  (let [initial-touch-y (atom 0.0)
        touch-diff-perc (fn [e] (+ @last-exp
                                   (/ (- @initial-touch-y (.-pageY (.-nativeEvent e))) expanded-height)))]
    (fn [{:keys [handle-style container-style closed-height expanded-height last-exp expanded
                 handle-content container-content prevent-expanding?]}]
      (h/concatv
        [c/animated-view {:name  "card-container"
                          :style (merge container-style
                                        {:position  :absolute :bottom 0
                                         :transform [{:translateY
                                                      (.interpolate
                                                        expanded
                                                        (clj->js {:inputRange  [0.0 1.0]
                                                                  :outputRange (if prevent-expanding?
                                                                                 [expanded-height expanded-height]
                                                                                 [expanded-height 0])}))}]
                                         :height    (+ closed-height expanded-height)})}
         (h/concatv
           [c/view {:name  "card-handle"
                    :style handle-style
                    :onStartShouldSetResponder
                           (constantly true) :onMoveShouldSetResponder (constantly true)
                    :onResponderGrant
                           (fn [e] (reset! initial-touch-y (.-pageY (.-nativeEvent e))))
                    :onResponderMove
                           (fn [e] (let [nv (min 1.0 (max 0.0 (touch-diff-perc e)))]
                                     (.setValue expanded nv)))
                    :onResponderRelease
                           (fn [e]
                             (let [closed-or-expanded (if (>= (touch-diff-perc e) 0.5) :expanded :closed)]
                               (animate-to last-exp expanded closed-or-expanded)))}]
           handle-content)]
        container-content))))