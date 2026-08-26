import { X } from "@phosphor-icons/react";

type ToastNotificationProps = {
  title: string;
  message: string;
  onDismiss: () => void;
};

export function ToastNotification({ title, message, onDismiss }: ToastNotificationProps) {
  return (
    <div className="app-toast" role="status" aria-live="polite">
      <div>
        <p className="app-toast__title">{title}</p>
        <p className="app-toast__message">{message}</p>
      </div>
      <button type="button" className="app-toast__dismiss" onClick={onDismiss} aria-label="Fechar notificação" title="Fechar notificação">
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
