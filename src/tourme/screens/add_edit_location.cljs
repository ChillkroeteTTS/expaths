(ns tourme.screens.add-edit-location
  (:require [reagent.core :as r]
            [tourme.style :as styl]
            [tourme.android.navigation :as n]
            [re-frame.core :as rf]
            [tourme.views.map :as m]
            [tourme.helper :as h]
            [tourme.components :as c]
            [tourme.views.views :as v]
            [tourme.config :as config]
            [tourme.views.card :as card]))

(def ReactNative (js/require "react-native"))
(def ReactNativeMaterialIcons (js/require "react-native-vector-icons/MaterialIcons"))
(def ReactNativeSwitchSelector (js/require "react-native-switch-selector"))
(def Keyboard (.-Keyboard ReactNative))

(def switch-selector (r/adapt-react-class (.-default ReactNativeSwitchSelector)))
(def toolbar-android (r/adapt-react-class (.-ToolbarAndroid ReactNativeMaterialIcons)))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def image (r/adapt-react-class (.-Image ReactNative)))
(def picker (r/adapt-react-class (.-Picker ReactNative)))
(def text-input (r/adapt-react-class (.-TextInput ReactNative)))

(defn valid? [{:keys [name location-type]}]
  (or (= :marker location-type) (not (empty? name))))

(defn location-edit [{:keys [location on-save enable-save?]}]
  (let [icons    {:place styl/place-ic :food styl/food-ic
                  :cafe  styl/cafe-ic :viewpoint styl/viewpoint-ic :marker styl/marker-ic}
        last-exp (r/atom 0.0)
        expanded (c/AnimatedValue. @last-exp)
        on-keyboard-show (fn [] (.setValue expanded 1.5))
        on-keyboard-hide (fn [] (.setValue expanded 1.0))]
    (r/create-class
      {:reagent-render
       (fn [{:keys [location on-save enable-save?]}]
         [c/view {:style {:flex 1}}
          [card/card {:closed-height   85 :expanded-height 150
                      :last-exp        last-exp :expanded expanded
                      :handle-style    {:background-color styl/prim :width "100%"}
                      :container-style {:background-color   styl/prim :width "100%"
                                        :padding-horizontal 20}
                      :handle-content
                                       [[v/explanation {:caption "Move the map to select a position"}]]
                      :container-content
                                       [[c/animated-view {:style {:height 150 :opacity expanded}}
                                         [c/text-input {:default-value  (:name @location) :placeholder "Name"
                                                        :style          (merge styl/font-heading2 {}) :max-length config/max-chars-name
                                                        :on-change-text (fn [value] (swap! location (fn [o] (assoc o :name value))))}]
                                         [c/text {:style {:font-size 10 :text-align :right :margin-right 5}}
                                          (str "(" (count (:name @location)) "/" config/max-chars-name ")")]
                                         [c/text-input {:default-value  (:description @location) :placeholder "Description (optional)"
                                                        :style          (merge styl/font-text {:font-size 11}) :multiline true :number-of-lines 3
                                                        :max-length     config/max-chars-loc-description
                                                        :on-change-text (fn [value] (swap! location (fn [o] (assoc o :description value))))}]
                                         [c/text {:style {:font-size 10 :text-align :right :margin-right 5}}
                                          (str "(" (count (:description @location)) "/" config/max-chars-loc-description ")")]]]}]
          [c/view {:style {:flex-direction  :row :position :absolute :bottom 10 :right 0 :left 0
                           :justify-content :space-between :padding-horizontal 20}}
           (into [c/view {:style {:flex-direction :row}}]
                 (for [type (keys icons)]
                   ^{:key type}
                   (let [selected? (= type (:location-type @location))]
                     [c/touchable-opacity
                      {:on-press (fn []
                                   (card/animate-to last-exp expanded (if (= type :marker) :closed :expanded))
                                   (swap! location #(assoc % :location-type type)))
                       :style    {:padding          6 :border-radius 30
                                  :background-color (if selected? styl/sec styl/prim)}}
                      [c/material-icon {:name  (get icons type) :size 25
                                        :color (if selected? styl/prim styl/marker-color)}]])))

           [c/touchable-opacity
            {:on-press on-save :disabled (not enable-save?)
             :style    {:padding       6 :background-color (if enable-save? styl/sec styl/color-disabled)
                        :border-radius 30 :justify-content :center :align-items :center}}
            [c/material-icon {:name "check" :size 25 :color styl/prim}]]]])
       :component-did-mount
       (fn []
         (js/setTimeout ;; prevent virtual keyboards from other screens that are not yet closed to open card view
           (fn []
             (.addListener c/Keyboard "keyboardDidShow" on-keyboard-show)
             (.addListener c/Keyboard "keyboardDidHide" on-keyboard-hide))
           500))
       :component-will-unmount
       (fn []
         (.removeListener c/Keyboard "keyboardDidShow" on-keyboard-show)
         (.removeListener c/Keyboard "keyboardDidHide" on-keyboard-hide))})))

(defn save-changes [save-fn]
  (.dismiss Keyboard)
  (save-fn)
  (n/pop! 1))

(defn loc-map-w-marker [tour location hidden-indices loc-pos start? end? colorize-markers]
  (c/expect-animation nil)
  (let [location-marker (styl/get-image (:location-type @location) :normal)]
    [c/view {:style {:height 428}}
     [m/location-map {:draggable-markers? false :shows-users-location? true
                      :on-region-change   (fn [pos]
                                            (reset! loc-pos
                                                    (select-keys (js->clj pos :keywordize-keys true)
                                                                 [:latitude :longitude])))
                      :custom-initial-pos @loc-pos :tour tour
                      :hidden-indices     hidden-indices
                      :colorize-markers   colorize-markers}]
     [view {:style {:position        :absolute :bottom 0 :top 0 :right 0 :left 0
                    :justify-content :center
                    :align-items     :center}}
      [image {:source ((keyword (str "img" (cond
                                             start? "-start"
                                             end? "-end"
                                             :else ""))) location-marker)
              :style  (merge
                        (get-in location-marker [:style :margin])
                        {:position :absolute :left "50%" :top "50%"
                         :width    (get-in location-marker [:style :w])
                         :height   (get-in location-marker [:style :h])})}]]]))

(defn toolbar [{:keys [on-save local-changes? show-discard-modal? location title]}]
  [toolbar-android {:navIconName   "arrow-back" :title title :height 55
                    :subtitle      ""
                    :style         {:elevation 2 :background-color styl/p-light}
                    :onIconClicked (fn [] (if (local-changes?)
                                            (reset! show-discard-modal? true)
                                            (n/pop! 1)))}])

(defn screen [{:keys [listen-to-screen location map-args on-save toolbar-title local-changes?] :as props}]
  (let [tour                (rf/subscribe [:editable-tour])
        show-discard-modal? (r/atom false)
        loc-pos             (atom (:position @location))
        toggle-modal        (fn [] (if (local-changes?)
                                     (swap! show-discard-modal? not)
                                     (n/pop! 1)) true)]
    (r/create-class
      {:reagent-render
       (fn [{:keys [listen-to-screen location map-args on-save toolbar-title local-changes? :as props]}]
         [view
          [view {:style {:flex-direction :column :background-color styl/prim :height "100%"}}
           [toolbar {:local-changes? local-changes? :show-discard-modal? show-discard-modal?
                     :location       @location :title toolbar-title
                     :on-save        on-save}]
           (h/concatv [loc-map-w-marker] map-args)
           [location-edit {:location     location :on-save (partial save-changes on-save)
                           :enable-save? (valid? @location)}]]
          (when @show-discard-modal?
            [v/discard-changes-modal show-discard-modal? (fn []
                                                           (reset! show-discard-modal? false)
                                                           (n/pop! 1))])])
       :component-will-mount
       (fn []
         (.addEventListener c/BackHandler "hardwareBackPress" toggle-modal))
       :component-will-unmount
       (fn [] (.removeEventListener c/BackHandler "hardwareBackPress" toggle-modal))})))

(defn alter-location [location loc-pos]
  (assoc (if (= :marker (:location-type location))
           (assoc location :name "" :description "")
           location)
    :position loc-pos))

(defn add-location-screen [props]
  (let [location  (r/atom {:name "" :description "" :location-type :marker :picture nil})
        tour      (rf/subscribe [:editable-tour])
        loc-pos   (atom (or (:position (last (:locations @tour)))
                            @(rf/subscribe [:position])))
        new-index (n/get-param props :new-index)
        start?    (= new-index 0)
        end?      (= new-index (count (:locations @tour)))]
    (fn [props]
      [screen
       (merge
         (js->clj props :keywordize-keys true)
         {:listen-to-screen "AddLocation" :location location :toolbar-title "Add Place"
          :local-changes?   (constantly true)
          :on-save          (fn [] (rf/dispatch [:tour-add-location
                                                 new-index
                                                 (alter-location @location @loc-pos)]))
          :map-args         [@tour location #{} loc-pos start? end? {:end? (not end?)}]})])))

(defn edit-location-screen [props]
  (let [index          (rf/subscribe [:selected-location-index])
        tour           (rf/subscribe [:editable-tour])
        location       (r/atom (nth (:locations @tour) @index))
        local-changes? (fn [] (not= @location (nth (:locations @tour) @index)))
        loc-pos        (atom (:position @location))]
    (fn [props]
      [screen
       (merge
         (js->clj props :keywordize-keys true)
         {:listen-to-screen "EditLocation" :location location :toolbar-title "Edit Place"
          :local-changes?   local-changes?
          :on-save          (fn [] (rf/dispatch [:tour-assoc-location @index
                                                 (alter-location @location @loc-pos)]))
          :map-args         [@tour location #{@index} loc-pos (= 0 @index)
                             (= (- (count (:locations @tour)) 1) @index) nil]})])))