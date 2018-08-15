(ns tourme.views.sign-in-up-forms
  (:require [tourme.style :as styl]
            [tourme.components :as c]
            [tourme.helper :as h]))

(defn err-text [err-msg]
  (when @err-msg
    [c/text {:style (merge styl/font-text {:font-size 13 :color :red :align-self :flex-start})}
     @err-msg]))

(defn container [& args]
  (h/concatv [c/view {:style (merge styl/absolute-filling-object
                                    {:background-color styl/prim :padding-top 20 :padding-left 25
                                     :align-items      :center :padding-right 25})}]
             args))

(defn button [props caption on-press]
  (let [style (:style props)
        rest (dissoc props :style)]
    [c/touchable-highlight (merge {:on-press on-press
                                   :style    (merge {:margin-top 20 :width 120 :background-color :white}
                                                    style)}
                                  rest)
     [c/text {:style (merge styl/font-button
                            {:background-color styl/sec :padding 10 :text-align :center})}
      caption]]))

(defn heading [caption]
  [c/text {:style (merge styl/font-heading1 {:font-size 33 :margin-bottom 30})}
   caption])

(defn input [{:keys [pw?]} caption input-a]
  [c/view {:style {:width "100%"}}
   [c/text {:style (merge styl/font-heading2
                          {:align-self    :flex-start :font-size 15
                           :margin-bottom 5 :color styl/sec})}
    caption]
   [c/text-input (assoc {:default-value  @input-a :style {:width "100%" :background-color styl/prim :border-radius 10 :margin-bottom 10}
                         :on-change-text (fn [value] (reset! input-a value))}
                   :secure-text-entry (or pw? false))]])