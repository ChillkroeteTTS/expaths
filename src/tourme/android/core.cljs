(ns tourme.android.core
  (:require [reagent.core :as r :refer [atom]]
            [re-frame.core :as rf]
            [tourme.effect-handlers] ;; same
            [tourme.events]
            [tourme.android.navigation :as n]
            [tourme.android.system :as s]
            [tourme.views.views :as v]
            [tourme.config :as config]
            [tourme.screens.tour :as tour]
            [tourme.screens.follow-tour :as ftour]
            [tourme.screens.add-edit-location :as a-e-location]
            [tourme.screens.home :as home]
            [tourme.screens.sign-in-up :as sign]
            [tourme.screens.password-reset :as pwreset]
            [tourme.screens.consent :as consent]
            [tourme.positioning :as p]
            [tourme.firebase.core :as fs]
            [tourme.subs]
            [tourme.debug-data :as dd]
            [goog.object :as object]
            [tourme.style :as styl]
            [tourme.logging :as l]
            [tourme.screens.menu :as m]
            [tourme.components :as c]))

(def ReactNative (js/require "react-native"))

(def app-registry (.-AppRegistry ReactNative))
(def debug true)

(defn setup-tick []
  (rf/dispatch [:tick])
  (js/setTimeout setup-tick 1000))

(defn alert [title]
  (.alert (.-Alert ReactNative) title))

(defn loading-screen []
  (let [{:keys [reasons show-loading-screen?]} @(rf/subscribe [:show-loading-screen?])]
    (when show-loading-screen?
      [v/loading-screen-overlay {:reasons reasons}])))

(defn message []
  (let [msg @(rf/subscribe [:system-message])]
    (when msg
      (c/expect-animation nil)
      [v/message msg])))

(defn app-root []
  [c/view {:style styl/absolute-filling-object}
   (cond
     (not @(rf/subscribe [:startup-ready?]))
     [consent/splash-screen]
     (and @(rf/subscribe [:signed-in?]) @(rf/subscribe [:accepted-policies?]))
     [(n/main-navigator {:Home         {:screen            (r/reactify-component home/home-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :EditTour     {:screen            (r/reactify-component tour/tour-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :FollowTour   {:screen            (r/reactify-component ftour/follow-tour-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :AddLocation  {:screen            (r/reactify-component a-e-location/add-location-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :EditLocation {:screen            (r/reactify-component a-e-location/edit-location-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :Menu         {:screen            (r/reactify-component m/menu-screen)
                                        :navigationOptions (clj->js {:header nil})}
                         :BugReport    {:screen            (r/reactify-component m/bug-report-screen)
                                        :navigationOptions (clj->js {:header nil})}})
      {:ref                        (fn [ref] (reset! n/top-level-navigator ref))
       :on-navigation-state-change n/on-navigation-change}]
     (not @(rf/subscribe [:signed-in?]))
     [(n/react-stack-navigator "LoginMethod"
                               {:SignUp        {:screen            (r/reactify-component sign/sign-up-screen)
                                                :navigationOptions (clj->js {:header nil})}
                                :SignIn        {:screen            (r/reactify-component sign/sign-in-screen)
                                                :navigationOptions (clj->js {:header nil})}
                                :PasswordReset {:screen            (r/reactify-component pwreset/enter-email-screen)
                                                :navigationOptions (clj->js {:header nil})}
                                :LoginMethod {:screen            (r/reactify-component sign/login-method-screen)
                                                :navigationOptions (clj->js {:header nil})}})]
     (and @(rf/subscribe [:signed-in?]) (false? @(rf/subscribe [:accepted-policies?])))
     [consent/consent-screen])
   [message]
   [loading-screen]])

(defn init []
  (enable-console-print!)
  (n/setup!)
  (rf/dispatch-sync [:initialize-db])
  (l/setup-logging!)
  (l/dprn :core/logging-check :enabled)
  (styl/init-styles!)
  (setup-tick)
  (s/set-up-location-hook)
  (when config/mock-http? (dd/mock-http))
  (when js/goog.DEBUG
    (object/set js/console "ignoredYellowBox" #js ["re-frame: overwriting"]))
  (fs/setup!)
  (.setLayoutAnimationEnabledExperimental (.-UIManager ReactNative) true)
  (.registerComponent app-registry "tourme" #(r/reactify-component app-root)))
