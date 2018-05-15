(ns status-im.chat.events.shortcuts
  (:require [status-im.ui.screens.wallet.send.events :as send.events]
            [status-im.ui.screens.wallet.choose-recipient.events :as choose-recipient.events]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.chat.events.input :as events.input]))

(def shortcuts
  {"send" (fn [db contact params]
            (-> db
                (send.events/set-and-validate-amount-db (:amount params))
                (choose-recipient.events/fill-request-details (select-keys contact [:name :address :whisper-identity]))
                (navigation/navigate-to :wallet-send-transaction)))})

(defn shortcut-override? [message]
  (get shortcuts (get-in message [:content :command])))

(defn shortcut-override-fx [db {:keys [chat-id content]}]
  (let [command              (:command content)
        contact              (get-in db [:contacts/contacts chat-id])
        shortcut-specific-fx (get shortcuts command)]
    {:db (-> db
             (shortcut-specific-fx contact (:params content))
             (events.input/clean-current-chat-command))}))