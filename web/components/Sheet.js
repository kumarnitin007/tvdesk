"use client";

import { useEffect } from "react";

export default function Sheet({ title, onClose, children, wide = false }) {
  useEffect(() => {
    function onKey(event) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    document.body.classList.add("sheet-open");
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.classList.remove("sheet-open");
    };
  }, [onClose]);

  return (
    <div className="sheet-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className={wide ? "sheet wide" : "sheet"} role="dialog" aria-modal="true" aria-label={title}>
        <div className="sheet-handle" />
        <header className="sheet-head">
          <button className="round-button" type="button" onClick={onClose} aria-label="Close">←</button>
          <h2>{title}</h2>
        </header>
        {children}
      </section>
    </div>
  );
}
