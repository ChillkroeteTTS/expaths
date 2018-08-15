(ns tourme.screens.sign-in-up
  (:require [tourme.components :as c]
            [tourme.style :as styl]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [tourme.views.views :as v]
            [tourme.views.sign-in-up-forms :refer [heading input button container err-text]]
            [tourme.android.navigation :as n]))

(defn not-empty? [& args]
  (every? identity (map not-empty args)))

(defn social [err-msg]
  (let [err-fn (fn [err]
                 (prn "alive and err " err)
                 (let [msg (condp = err
                             :account-exists-with-different-credentials
                             "You already used this email address to sign in with another provider before."
                             "Unknown error. Please try another login method.")]
                   (reset! err-msg msg)))]
    [c/view {:style {:align-items :center :justify-content :space-evenly :margin-top 10 :margin-bottom 10}}
     [c/touchable-highlight {:style          {:width 190 :height 30 :margin-bottom 10}
                             :underlay-color "rgba(0,0,0,0.6)"
                             :on-press       (fn [] (rf/dispatch [:sign-in-social :facebook
                                                                  identity
                                                                  err-fn]))}
      [c/view {:style {:width       190 :height 30 :background-color "#3B5998"
                       :align-items :center :justify-content :center}}
       [c/text {:style (merge styl/font-button {:font-size 13})} "Sign in with Facebook"]]]
     [c/google-sign-in-button {:style    {:width 196 :height 35}
                               :size     c/google-sign-in-size-wide
                               :on-press (fn [] (rf/dispatch [:sign-in-social :google
                                                              identity
                                                              err-fn]))}]]))

(defn sign-up-screen []
  (let [email      (r/atom "")
        pw         (r/atom "")
        pw-repeat  (r/atom "")
        err-msg    (r/atom nil)
        pp         (r/atom false)
        gtac       (r/atom false)
        validation (fn []
                     (let [err (cond
                                 (empty? @email) "Please provide an email address"
                                 (empty? @pw) "Please provide a password with at least 6 characters"
                                 (empty? @pw-repeat) "Please confirm your password"
                                 (not= @pw-repeat @pw) "Passwords do not match"
                                 (not (and @pp @gtac)) "Please accept the Privacy Policy and Terms of Use to continue."
                                 :else nil)]
                       {:err? (some? err) :msg err}))]
    (fn [props]
      [c/scroll-view {:style                   {:height "100%" :background-color :white}
                      :content-container-style {:background-color styl/prim :padding-top 20
                                                :padding-left     25 :align-items :center
                                                :padding-right 25}}
       [heading "Sign Up"]
       [input {} "Email" email]
       [input {:pw? true} "Password" pw]
       [input {:pw? true} "Confirm Password" pw-repeat]
       [err-text err-msg]
       [v/toggleable-element pp "Privacy Policy" "https://chillkroetenteich.de/pp.html"]
       [v/toggleable-element gtac "Terms of Use" "https://chillkroetenteich.de/gtac.html"]
       [button {}
        "Sign Up"
        (fn []
          (let [{err? :err? msg :msg} (validation)]
            (if (not err?)
              (rf/dispatch [:sign-up @email @pw identity ;; re-frame state change triggers home screen rendering
                            (fn [e]
                              (let [inv-cred  "Email already exists"
                                    inv-email "Please provide a valid email address"
                                    unknown   "Something unexpected happened. We are terribly sorry for the inconvenience. This could be due to a missing network connection."]
                                (c/expect-animation nil)
                                (reset! err-msg
                                        (condp = e
                                          :email-already-in-use
                                          inv-cred
                                          :invalid-email
                                          inv-email
                                          unknown))))])
              (do (c/expect-animation nil) (reset! err-msg msg)))))]
       [c/view {:style {:align-items :center :margin-top 10}}
        [c/text
         "Already signed up? "]
        [c/view {:style {:flex-direction :row}}
         [v/link {:on-press (fn [] (n/navigate props "SignIn")) :color styl/sec :clicked-color styl/sec-d
                  :caption  "Sign in"}]
         [c/text
          " or "]]]])))

(defn sign-in-screen []
  (let [email   (r/atom "")
        pw      (r/atom "")
        err-msg (r/atom nil)]
    (fn [props]
      [container
       [heading "Sign In"]
       [input {} "Email" email]
       [input {:pw? true} "Password" pw]
       [err-text err-msg]
       [button {}
        "Sign In"
        (fn []
          (if (not-empty? @email @pw)
            (rf/dispatch [:sign-in @email @pw identity
                          (fn [e]
                            (let [inv-cred  "Invalid credentials provided"
                                  inv-email "Please provide a valid email address"
                                  disabled  "User was disabled"
                                  unknown   "Something unexpected happened. We are terribly sorry for the inconvenience. This could be due to a missing network connection."]
                              (c/expect-animation nil)
                              (reset! err-msg
                                      (condp = e
                                        :invalid-credentials
                                        inv-cred
                                        :invalid-email
                                        inv-email
                                        :disabled-user
                                        disabled
                                        unknown))))])
            (reset! err-msg "Please fill out all fields.")))]
       [v/link {:on-press      (fn [] (n/navigate props "PasswordReset")) :color styl/sec
                :clicked-color styl/sec-d :caption "Password forgotten?"
                :style         {:margin-top 15}}]
       [v/filler]])))

(defn login-method-screen [props]
  (let [err-msg (r/atom nil)]
    (fn [props]
      [container
       [c/image {:source styl/logo-image
                 :style  {:width 150 :height 150}}]
       [c/view {:style {:justify-content :center :align-items :center :flex 2}}
        [button {:style {:width         220
                         :margin-bottom 25}}
         "Sign In"
         (fn [] (n/navigate props "SignIn"))]
        [button {:style {:width         220
                         :margin-bottom 25}}
         "Sign Up"
         (fn [] (n/navigate props "SignUp"))]
        [button {:style {:width         220
                         :margin-bottom 25}}
         "Try Out"
         (fn []
           (.alert c/Alert
                   "Are you sure?"
                   (str "You can't explore or create tours while in try out mode."
                        "If you decide to create an account just go to the menu.")
                   (clj->js [{:text "Cancel" :style :cancel}
                             {:text    "Continue"
                              :onPress (fn []
                                         (rf/dispatch [:sign-in-anonymously
                                                       (fn [] (n/navigate props "Consent"))
                                                       c/connection-issue-alert]))}])))]]
       [err-text err-msg]

       [social err-msg]])))