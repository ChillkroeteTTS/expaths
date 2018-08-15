(ns tourme.screens.tour
  (:require [re-frame.core :as rf]
            [tourme.views.views :as v]
            [tourme.views.map :as m]
            [tourme.views.location-cinema :as lcin]
            [tourme.helper :as h]
            [tourme.android.system :as s]
            [tourme.style :as styl]
            [reagent.core :as r]
            [tourme.android.navigation :as n]
            [tourme.components :as c]
            [tourme.config :as config]
            [tourme.views.card :as card]))

(def ReactNative (js/require "react-native"))
(def ReactNativeMaterialIcons (js/require "react-native-vector-icons/MaterialIcons"))
(def Keyboard (.-Keyboard ReactNative))

(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def image (r/adapt-react-class (.-Image ReactNative)))
(def text-input (r/adapt-react-class (.-TextInput ReactNative)))
(def scroll-view (r/adapt-react-class (.-ScrollView ReactNative)))
(def material-icon (r/adapt-react-class (.-default ReactNativeMaterialIcons)))
(def toolbar-android (r/adapt-react-class (.-ToolbarAndroid ReactNativeMaterialIcons)))

(defn valid-tour? [tour] (and (>= (count (:locations tour)) 2)
                              (>= (count (:name tour)) 3)))

(defn navigation-bar [props on-arrow-clicked]
  (let [tour (rf/subscribe [:editable-tour])]
    (fn [props]
      [toolbar-android {:navIconName        "arrow-back" :title "Edit Tour" :height 55 :width "100%"
                        :subtitle           ""
                        :style              {:elevation 2 :background-color styl/p-light}
                        :onIconClicked      on-arrow-clicked
                        :actions            [{:title "Save" :iconName "check" :show :always}]
                        :on-action-selected (fn [i]
                                              (.dismiss Keyboard)
                                              (if (valid-tour? @tour)
                                                (rf/dispatch [:save-current-tour
                                                              (fn [_] (n/pop props 1))
                                                              c/connection-issue-alert])
                                                (js/alert "Please provide at least 2 locations and a name with at least 3 characters.")))}])))

(defn tour-name [tour expanded]
  [view {:style {:width           "100%" :background-color styl/prim
                 :justify-content :center :align-items :center}}
   [text-input {:style                   {:font-family "OpenSans-Bold" :font-size 20 :color :black
                                          :width       "50%" :text-align :center :margin-top -5}
                :onChangeText            (fn [v] (rf/dispatch [:tour-set-name v]))
                :placeholder             "Title"
                :max-length              config/max-chars-name
                :underline-color-android styl/sec
                :default-value           (:name @tour)}]

   [c/animated-view {:style {:opacity (.interpolate expanded (clj->js {:inputRange  [0.0 1.0]
                                                                       :outputRange [(if (empty? (:locations @tour))
                                                                                       0.0
                                                                                       0.2)
                                                                                     1]}))}}
    [text-input {:style                   (merge styl/font-text {:font-size 11 :text-align :center
                                                                 :min-width "70%"})
                 :onChangeText            (fn [v] (rf/dispatch [:tour-set-description v]))
                 :placeholder             "Description (optional)" :multiline true
                 :number-of-lines         3
                 :max-length              config/max-chars-loc-description
                 :underline-color-android styl/sec
                 :default-value           (:description @tour)}]]])

(defn register-back-handler [props show-discard-modal? local-changes?]
  (let [toggle-modal (fn [] (if (local-changes?)
                              (swap! show-discard-modal? not)
                              (n/pop props 1)) true)]
    (n/listening-for-screen-switch!
      "EditTour"
      (fn []
        (.addEventListener c/BackHandler "hardwareBackPress" toggle-modal))
      (fn [] (.removeEventListener c/BackHandler "hardwareBackPress" toggle-modal)))))

(defn tour-screen []
  (let [tour                (rf/subscribe [:editable-tour])
        props               (r/props (r/current-component))
        first-pos           (:position (first (:locations @tour)))
        show-discard-modal? (r/atom false)
        local-changes?      (fn [] (not= (:locations @tour)
                                         (:locations (get @(rf/subscribe [:tours-map])
                                                          (:id @tour)))))
        last-exp            (r/atom 0.0)
        expanded            (c/AnimatedValue. @last-exp)]
    (r/create-class
      {:reagent-render
       (fn [props]
         [view
          [view {:style {:flex-direction "column" :align-items "center" :justify-content :flex-start
                         :height         "100%"}}
           [navigation-bar props (fn [] (if (local-changes?)
                                          (reset! show-discard-modal? true)
                                          (n/pop props 1)))]
           [m/location-map {:draggable-markers? true :tour @tour
                            :mate-watch         (rf/subscribe [:selected-location-position])
                            :custom-initial-pos (when first-pos first-pos)
                            :on-marker-press    (fn [i _] (rf/dispatch [:tour-select-location i]))}]

           [card/card {:closed-height   (if (empty? (:locations @tour)) 130 150) :expanded-height 100
                       :last-exp        last-exp :expanded expanded
                       :handle-style    {:background-color styl/prim :width "100%"}
                       :container-style {:background-color styl/prim :width "100%"
                                         :padding-bottom   50}
                       :handle-content
                                        [[v/explanation {:caption "Edit or Add a Place"
                                                         :up      {:animation-value expanded
                                                                   :on-press        (fn []
                                                                                      (card/animate-to last-exp
                                                                                                       expanded
                                                                                                       (if (>= @last-exp 0.5)
                                                                                                         :closed :expanded)))}}]]
                       :container-content
                                        [[tour-name tour expanded]
                                         [c/view {:style {:flex 1}}]]}]
           [lcin/loc-cinema-with-buttons
            {:on-add    (fn [] (let [sel-index @(rf/subscribe [:selected-location-index])
                                     new-index (if sel-index (+ sel-index 1) 0)]
                                 (n/navigate props "AddLocation"
                                             {:new-index new-index})))
             :on-edit   (partial n/navigate props "EditLocation")
             :on-delete (partial rf/dispatch [:tour-remove-selected-location])}]]
          (when @show-discard-modal?
            [v/discard-changes-modal show-discard-modal? (fn []
                                                           (reset! show-discard-modal? false)
                                                           (n/pop props 1))])])
       :component-will-mount
       (fn []
         (n/register-nav-listener :tour-screen (register-back-handler props show-discard-modal?
                                                                      local-changes?))
         (if (not (empty? (:locations @tour))) (rf/dispatch [:tour-select-location 0])
                                               (rf/dispatch [:tour-select-location nil])))
       :component-will-unmount
       (fn []
         (rf/dispatch [:tour-select-location nil])
         (n/deregister-nav-listener :tour-screen))})))