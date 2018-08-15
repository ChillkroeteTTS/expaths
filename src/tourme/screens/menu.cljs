(ns tourme.screens.menu
  (:require [tourme.components :as c]
            [tourme.style :as styl]
            [tourme.android.navigation :as n]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [tourme.views.views :as v]
            [tourme.logging :as l]))

(def links {"Send Bug Report" "BugReport"})

(defn list-item [{:keys [on-press]} item]
  [c/touchable-highlight {:on-press on-press :underlay-color styl/prim-d}
   [c/view {:background-color    styl/prim
            :border-bottom-width 1
            :border-color        styl/prim-d
            :padding-top         15 :padding-bottom 15
            :padding-left        10}
    [c/text {:style (merge styl/font-heading2
                           {:font-family "OpenSans-Regular"})}
     item]]])

(defn menu-screen []
  (fn [props]
    (let [user @(rf/subscribe [:user])
          account-header-str (str "Account - " (if (:anonymous? user)
                                                 "Anonymous"
                                                 (:email user)))]
      [c/view
       [c/toolbar-android {:navIconName   "arrow-back" :title "Menu" :height 55
                           :subtitle      ""
                           :style         {:elevation 2 :background-color styl/prim}
                           :onIconClicked (fn [] (n/pop props 1))}]
       [c/view {:style {:align-items      :center :width "100%" :height "100%"
                        :background-color styl/prim :padding-top 5}}
        [c/view {:width "80%" :padding 10}
         [c/section-list
          {:renderItem          (fn [jitem]
                                  ;;#js {:item "Send Bug Report", :index 0, :section #js {:title "Debug", :data #js ["Send Bug Report"]}, :separators #js {:highlight #object[highlight], :unhighlight #object[unhighlight], :updateProps #object[updateProps]}}
                                  (let [item  (.-item jitem)
                                        sign-out-fn (fn [] (rf/dispatch-sync [:sign-out]))]
                                    (r/as-element
                                      [list-item
                                       {:on-press (condp = item
                                                    "Send Bug Report" (fn [] (n/navigate props (get links item)))
                                                    "Create an Account" sign-out-fn
                                                    "Sign Out" sign-out-fn
                                                    :else (fn [] (l/dprn :menu :unknown-item-pressed item)))}
                                       item])))
           :renderSectionHeader (fn [jtitle]
                                  ;;#js {:section #js {:title "Debug", :data #js ["Send Bug Report"]}}
                                  (r/as-element
                                    [c/view {:border-bottom-width 1 :padding-bottom 5
                                             :border-color        styl/sec :margin-top 35}
                                     [c/text {:style styl/font-heading1} (.. jtitle -section -title)]]))
           :sections            (clj->js [{:title account-header-str
                                           :data  [(if (:anonymous? user) "Create an Account" "Sign Out")]}
                                          {:title "System" :data ["Send Bug Report"]}])
           :keyExtractor        (fn [item index] index)
           }]]]])))

(defn bug-report-screen []
  (let [msg (r/atom "")]
    (fn [props]
      [c/view {:style {:width "100%" :flex 1}}
       [c/toolbar-android {:navIconName   "arrow-back" :title "Send Bug Report" :height 55
                           :subtitle      ""
                           :style         {:elevation 2 :background-color styl/prim}
                           :onIconClicked (fn [] (n/pop props 1))}]
       [c/view {:style {:align-items      :center :width "100%" :flex 1
                        :background-color styl/prim :padding-top 40}}
        [c/text {:style (merge styl/font-text {:font-size 14})}
         "Please provide a bug description"]
        [c/text-input {:multiline      true :number-of-lines 10
                       :style          {:width "80%"}
                       :on-change-text (fn [value] (reset! msg value))}]
        [v/bottom-button {:on-press (fn []
                                      (rf/dispatch [:send-bug-report @msg])
                                      (n/pop props 2))
                          :title    "Send"}]]])))