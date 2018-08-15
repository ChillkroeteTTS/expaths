(ns tourme.screens.home
  (:require [tourme.android.navigation :as n]
            [tourme.views.views :as v]
            [tourme.style :as styl]
            [tourme.views.map :as m]
            [re-frame.core :as rf]
            [tourme.android.system :as s]
            [reagent.core :as r]
            [tourme.helper :as h]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs-time.format :as tf]
            [cljs-time.core :as t]
            [tourme.debug-data :as dd]
            [tourme.components :as c]
            [cljs.core.async :refer [chan <!]])
  (:require-macros [cljs.core.async :refer [go-loop]]))


(defn top-bar [props selected-tour]
  [c/view {:style {:flex-direction   :row :height 50
                   :background-color styl/prim :width "100%" :elevation 8 :align-items :center}}
   (when (or goog.DEBUG tourme.config/INTERNALTEST)
     [c/touchable-opacity {:on-press (fn [] (n/navigate props "Menu"))
                           :style    {:margin-right 10 :margin-left 10}}
      [c/material-icon {:name "menu" :size 24}]])
   [c/text {:style styl/font-heading-top-bar}
    "ExPaths"]])

(defn tour-representation [props tour selected-tour selected?]
  (let [format-distance (fn [distance] (if distance (str (gstring/format "%.1f km" (/ distance 1000)))
                                                    "- -"))])
  [c/touchable-highlight {:on-press       (if selected?
                                            (fn [] (rf/dispatch [:selected-tour-id nil]))
                                            (fn [] (rf/dispatch [:selected-tour-id (:id tour)])))
                          :onLayout       (:onLayout props)
                          :underlay-color styl/prim-d}
   [c/view {:style {:width               "100%" :flex-direction :row
                    :background-color    (if selected? styl/prim-d styl/prim) :height 128
                    :border-bottom-color "rgba(207,207,207,1)" :border-bottom-width 1
                    :justify-content     :flex-start}}
    [c/view {:name "text" :style {:margin-left 10 :margin-top 6 :flex-direction :column :height 128
                                  :width       "80%"}}
     [c/text {:style (merge styl/font-heading2 {:font-size 22})}
      (:name tour)]
     [v/rating-star-bar
      {:interactive? false :container-style {:justify-content :flex-start}}
      (:rating tour)]
     [c/scroll-view {:style {:margin-top 10 :margin-bottom 30}}
      [c/text {:style styl/font-text}
       (if (empty? (:description tour))
         "No description provided."
         (:description tour))]]]
    [c/view {:name "properties" :style {:flex   1 :align-self :flex-end
                                        :height "100%" :justify-content :space-evenly}}
     [v/property "access-time" (h/duration->str (:duration tour))]
     [v/property "near-me" (h/distance->str (:distance tour))]]]])

(defn tours-list [{:keys [selected-tour filter-tours?]}]
  (let [db-tours    (rf/subscribe [:tours])
        refreshing? (rf/subscribe [:fetching-tours?])
        animating?  (r/atom false)
        scroll-view (atom nil)
        tour-reprs  (atom {})]
    (rf/dispatch [:register-watch :home/scroll-to-selected-tour
                  (fn []
                    (let [sel-id @selected-tour
                          tr     (get @tour-reprs sel-id)
                          sv     @scroll-view]
                      (when (and tr sv) (.scrollTo sv (clj->js {:x 0 :y tr :animated true})))))])
    (r/create-class
      {:reagent-render
       (fn [{:keys [selected-tour filter-tours?]}]
         (let [tours (sort-by :distance ((if filter-tours?
                                           #(filter (fn [tour] (= (:creator tour)
                                                                  (:id @(rf/subscribe [:user]))))
                                                    %)
                                           identity) @db-tours))]
           (when (not @animating?)
             (reset! animating? true)
             (c/expect-animation (fn [] (reset! animating? false))))
           [c/view {:style {:background-color styl/prim :flex 1 :width "100%" :elevation 8}}
            [c/scroll-view {:refresh-control (r/as-element
                                               [c/refresh-control
                                                {:pointer-events :box-none
                                                 :on-refresh     (fn []
                                                                   (rf/dispatch [:refresh-tours identity]))
                                                 :refreshing     @refreshing?
                                                 :tint-color     styl/sec}])
                            :ref             (partial h/ra! scroll-view)}
             (doall
               (for [{id :id :as tour} tours]
                 (let [selected? (= id @selected-tour)]
                   ^{:key id}
                   [tour-representation {:onLayout (fn [event]
                                                     (let [y (.. event -nativeEvent -layout -y)]
                                                       (swap! tour-reprs #(assoc % id y))))}
                    tour selected-tour selected?])))
             (when @refreshing?
               [c/view {:style styl/absolute-filling-object :background-color "rgba(0,0,0,0.2)"}])]]))
       :component-will-unmount
       (fn []
         (rf/dispatch [:deregister-watch :home/scroll-to-selected-tour]))})))

(defn list-header [props selected-tour filter? range]
  (let [user-id    (:id @(rf/subscribe [:user]))
        anonymous? (:anonymous? @(rf/subscribe [:user]))
        tour       (get @(rf/subscribe [:tours-map]) @selected-tour)
        creator-id (:creator tour)
        on-add     (fn []
                     (rf/dispatch-sync [:edit-new-tour])
                     (n/navigate props "EditTour"))
        on-edit    (fn []
                     (rf/dispatch-sync [:edit-tour @selected-tour])
                     (n/navigate props "EditTour"))
        on-delete  (fn []
                     (rf/dispatch [:delete-tour (:id tour)
                                   (fn []
                                     (rf/dispatch [:refresh-tours]))
                                   (fn []
                                     (js/alert "Upss... There was a problem deleting your tour. Please try it again later."))]))
        editable?  (and (some? @selected-tour) (or (nil? creator-id) (= creator-id user-id)))
        deletable? (and (some? @selected-tour) (or (nil? creator-id) (= creator-id user-id)))]
    (h/concatv [c/view {:width         "100%" :padding-vertical 5
                        :elevation     8 :background-color styl/prim :align-items :center
                        :margin-bottom 1 :flex-direction :row :justify-content :space-between}]
               (if (not anonymous?)
                 [[c/view {:style {:flex-direction :row :background-color :white}}
                   [c/touchable-opacity {:on-press on-add}
                    [c/material-icon {:name  "add" :size 20 :color styl/sec
                                      :style {:padding-horizontal 7}}]]
                   [c/touchable-opacity {:on-press on-edit :disabled (not editable?)}
                    [c/material-icon {:name  "edit" :size 20 :color (if editable? styl/sec styl/color-disabled)
                                      :style {:padding-horizontal 7}}]]
                   [c/touchable-opacity {:on-press on-delete :disabled (not deletable?)}
                    [c/material-icon {:name  "delete-forever" :size 20 :color (if deletable? styl/sec styl/color-disabled)
                                      :style {:padding-horizontal 7}}]]]
                  [c/touchable-opacity {:on-press       (fn []
                                                          (rf/dispatch-sync [:follow-tour @selected-tour])
                                                          (n/navigate props "FollowTour"))
                                        :active-opacity 0.6 :disabled (nil? @selected-tour)}
                   [c/text {:style (merge styl/font-heading1 (if @selected-tour {} {:color styl/color-disabled}))}
                    "Explore!"]]
                  [c/view {:style {:flex-direction :row :align-items :center}}
                   [c/text {:style (merge styl/font-text {:font-size 11})} "Own Tours"]
                   [c/switch {:value         @filter? :on-value-change (fn [val] (reset! filter? val))
                              :on-tint-color styl/sec-l :thumb-tint-color styl/sec}]]]
                 [[c/view {:style {:flex-direction :row :width "100%" :justify-content :center}}
                   [c/text {:style (merge styl/message-font {})}
                    "Please "]
                   [v/link {:on-press      (fn []
                                             (rf/dispatch-sync [:sign-out])) :color styl/sec
                            :clicked-color styl/sec-d :caption "Sign Up"
                            :style         {}}]
                   [c/text {:style (merge styl/message-font {})}
                    " to create or explore tours."]]]))))

(defn calc-distance-on-pos-change! []
  (let [_ @(rf/subscribe [:position])]
    (when (not @(rf/subscribe [:fetching-tours?]))
      (rf/dispatch [:distance-tour-data]))))

(defn register-watches []
  (let [on-focus (fn []
                   (rf/dispatch [:register-watch
                                 :home/calc-distance-on-pos-change!
                                 calc-distance-on-pos-change!])
                   (rf/dispatch [:register-watch
                                 :home/select-tour-watch
                                 (fn []
                                   (c/expect-animation nil))]))
        on-blur  (fn []
                   (rf/dispatch [:selected-tour-id nil])
                   (rf/dispatch [:deregister-watch :home/select-tour-watch])
                   (rf/dispatch [:deregister-watch :home/calc-distance-on-pos-change!]))]
    (rf/dispatch [:register-watch :home/wait-for-tours
                  (fn []
                    (let [loaded? @(rf/subscribe [:tour-loaded?])]
                      (when loaded?
                        (js/setTimeout on-focus 1000)
                        (rf/dispatch [:deregister-watch :home/wait-for-tours]))))])
    (n/listening-for-screen-switch! "Home" on-focus on-blur)))

(defn tour-start-markers [tours selected-tour]
  (mapv
    (fn [tour]
      (let [first-loc (first (:locations tour))]
        [m/location-marker "" ;; prevent default callout
         (:location-type first-loc) (:position first-loc) identity
         false true false (fn [] (rf/dispatch [:selected-tour-id (:id tour)]))]))
    (vals tours)))

(defn home-screen []
  (let [tours              (rf/subscribe [:tours-map])
        dirty-info-status? (rf/subscribe [:dirty-info-status?])
        selected-tour      (rf/subscribe [:selected-tour-id])
        filter-tours?      (r/atom false)
        range              (r/atom 10)]
    (r/create-class
      {:reagent-render
       (fn [props]
         [c/view {:style {:flex-direction  :column :align-items :center
                          :justify-content :flex-start :height "100%"}}
          [top-bar props @selected-tour]
          [m/location-map {:draggable-markers? false
                           :mfte-watch         selected-tour
                           :on-region-change   (fn [pos]
                                                 (let [info (js->clj pos :keywordize-keys true)]
                                                   (rf/dispatch [:refresh-info
                                                                 {:position     (select-keys info
                                                                                             [:latitude :longitude])
                                                                  :radius-in-km (-> (:latitudeDelta info)
                                                                                    (* 111)
                                                                                    (/ 2)
                                                                                    (double))}])))
                           :tour               (get @tours @selected-tour)
                           :height             300
                           :custom-markers     (when (nil? @selected-tour)
                                                 (tour-start-markers @tours selected-tour))
                           :on-map-press       (fn [] (rf/dispatch [:selected-tour-id nil]))}]
          [list-header props selected-tour filter-tours? range]
          [tours-list {:selected-tour selected-tour :filter-tours? @filter-tours?}]
          (when @dirty-info-status?
            [c/touchable-opacity {:on-press (fn []
                                              (rf/dispatch [:dirty-info-status? false])
                                              (rf/dispatch [:refresh-tours]))
                                  :style    {:position :absolute :top 290} :active-opacity 0.8}
             [m/message "Reload Tours" {:width 90}]])])
       :component-did-mount
       (fn [this] (n/register-nav-listener :home-screen (register-watches)))
       :component-will-unmount
       (fn [this]
         (n/deregister-nav-listener :home-screen))})))
