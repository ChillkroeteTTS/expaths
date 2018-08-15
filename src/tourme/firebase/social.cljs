(ns tourme.firebase.social
  (:require [cljs.core.async :refer [>! <! chan put!]]
            [tourme.logging :as l])
  (:require-macros [cljs.core.async :refer [go go-loop]]))

(def fb (.-default (js/require "react-native-firebase")))
(def GoogleSignIn (.-GoogleSignin (js/require "react-native-google-signin")))
(def fbsdk (js/require "react-native-fbsdk"))
(def login-manager (.-LoginManager fbsdk))
(def access-token (.-AccessToken fbsdk))


(defn wait-for-sign-in! [sign-in-chan on-success on-failure]
  (go
    (let [sign-in-res (<! sign-in-chan)]
      (if-let [code (.-code sign-in-res)]
        (condp = code
          "auth/account-exists-with-different-credential"
          (do (l/dprn :social/sign-in :account-exists-with-different-credentials)
              (on-failure :account-exists-with-different-credentials)))
        (do
          (let [profile (.. sign-in-res -additionalUserInfo -profile)]
            (l/dprn :social/sign-in :received-user)
            (on-success
              {:js-obj     sign-in-res
               :id         (.-id profile)
               :name       (.-name profile)
               :email      (.-email profile)
               :anonymous? false})))))))

(defn wait-for-creds! [get-creds-chan put-sign on-failure]
  (go
    (let [creds-res (<! get-creds-chan)
          err       (.-code creds-res)]
      (if err
        (let [err-msg  (condp = err
                         12501 [:social/google :signIn :canceled]
                         12502 [:social/google :signIn :already-in-progress]
                         [:social/google :signIn :unknown-error])
              clj-code (last err-msg)]
          (when (= :unknown-error clj-code)
            (l/dprn (str creds-res))
            (l/dprn err))
          (apply l/dprn err-msg)
          (on-failure clj-code))
        (do
          (l/dprn :social/google :signIn :received-creds)
          (.then
            (.signInAndRetrieveDataWithCredential
              (.auth fb)
              ((.. fb -auth -GoogleAuthProvider -credential) (.-idToken creds-res) (.-accessToken creds-res)))
            put-sign put-sign))))))

(defn google-sign-in [on-success on-failure]
  (let [conf-chan      (chan)
        put-conf       (partial put! conf-chan)
        get-creds-chan (chan)
        put-creds      (partial put! get-creds-chan)
        sign-in-chan   (chan)
        put-sign!      (partial put! sign-in-chan)]
    (l/dprn :social/google :configure)
    (wait-for-creds! get-creds-chan put-sign! on-failure)
    (wait-for-sign-in! sign-in-chan on-success on-failure)
    (.then (.configure GoogleSignIn) put-conf put-conf)
    (go
      (let [config-res (<! conf-chan)]
        (if (true? config-res)
          (do (l/dprn :social/google :signIn)
              (.then (.signIn GoogleSignIn) put-creds put-creds))
          (on-failure config-res))))))

;; --------------------------------- FACEBOOK --------------------------------------------

(defn wait-for-login! [login-chan put-token on-failure]
  (go
    (let [res (<! login-chan)]
      (if (.-isCancelled res)
        (do
          (l/dprn :social/fb :user-cancelled)
          (on-failure))
        (do
          (l/dprn :social/fb :login-success)
          (.then (.getCurrentAccessToken access-token) put-token put-token))))))

(defn wait-for-token! [token-chan put-sign on-failure]
  (go
    (let [res (<! token-chan)]
      (if (nil? res)
        (do
          (l/dprn :social/fb :unknown-token-err)
          (on-failure))
        (do
          (l/dprn :social/fb :received-token)
          (.then
            (.signInAndRetrieveDataWithCredential
              (.auth fb)
              ((.. fb -auth -FacebookAuthProvider -credential) (.-accessToken res)))
            put-sign put-sign))))))

(defn facebook-sign-in [on-success on-failure]
  (let [login-chan (chan)
        put-login  (partial put! login-chan)
        token-chan (chan)
        put-token  (partial put! token-chan)
        sign-chan  (chan)
        put-sign   (partial put! sign-chan)]
    (l/dprn :social/fb :login)
    (.then (.logInWithReadPermissions login-manager (clj->js ["public_profile" "email"]))
           put-login put-login)
    (wait-for-sign-in! sign-chan on-success on-failure)
    (wait-for-token! token-chan put-sign on-failure)
    (wait-for-login! login-chan put-token on-failure)))
