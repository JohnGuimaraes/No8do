import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";

type ConfirmationDialogProps = {
  open: boolean;
  title: string;
  message: string;
  itemName?: string;
  confirmLabel: string;
  loadingLabel: string;
  destructive?: boolean;
  loading?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function ConfirmationDialog({ open, title, message, itemName, confirmLabel, loadingLabel, destructive = false, loading = false, onCancel, onConfirm }: ConfirmationDialogProps) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    cancelButtonRef.current?.focus();
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !loading) onCancel();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [loading, onCancel, open]);

  if (!open) return null;

  return <div className="fixed inset-0 z-[70] flex items-end bg-foreground/20 px-3 py-4 sm:items-center sm:justify-center sm:px-6" onMouseDown={() => { if (!loading) onCancel(); }}>
    <section className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-xl" role="alertdialog" aria-modal="true" aria-labelledby="confirmation-dialog-title" onMouseDown={(event) => event.stopPropagation()}>
      <h2 id="confirmation-dialog-title" className="text-lg font-semibold text-foreground">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">{message}</p>
      {itemName ? <p className="mt-3 break-words rounded-md border border-border bg-muted/45 px-3 py-2 text-sm font-medium text-foreground">{itemName}</p> : null}
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button ref={cancelButtonRef} type="button" variant="outline" disabled={loading} onClick={onCancel}>Cancelar</Button>
        <Button type="button" variant={destructive ? "destructive" : "default"} disabled={loading} onClick={onConfirm}>{loading ? loadingLabel : confirmLabel}</Button>
      </div>
    </section>
  </div>;
}
