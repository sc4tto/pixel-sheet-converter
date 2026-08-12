from __future__ import annotations

import json
import queue
import threading
from pathlib import Path
import tkinter as tk
from tkinter import colorchooser, filedialog, messagebox, ttk

from PIL import Image, ImageTk

from .converter import ConversionResult, convert_image, target_size
from .exporters import export_png, export_xlsx
from .palettes import BUILTIN_PALETTES, normalize_palette


class PixelSheetApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Pixel Sheet Converter")
        self.geometry("1240x780")
        self.minsize(1040, 680)
        self.source_path: Path | None = None
        self.source_image: Image.Image | None = None
        self.result: ConversionResult | None = None
        self.preview_original = None
        self.preview_result = None
        self.events: queue.Queue = queue.Queue()
        self.palette_colors = BUILTIN_PALETTES["RGB primari"].copy()
        self._create_style()
        self._create_ui()
        self.after(100, self._poll_events)

    def _create_style(self):
        style = ttk.Style(self)
        if "vista" in style.theme_names():
            style.theme_use("vista")
        style.configure("Title.TLabel", font=("Segoe UI", 19, "bold"), foreground="#17365D")
        style.configure("Section.TLabelframe.Label", font=("Segoe UI", 10, "bold"), foreground="#17365D")
        style.configure("Primary.TButton", font=("Segoe UI", 10, "bold"))

    def _create_ui(self):
        root = ttk.Frame(self, padding=14)
        root.pack(fill="both", expand=True)
        ttk.Label(root, text="Pixel Sheet Converter", style="Title.TLabel").pack(anchor="w")
        ttk.Label(root, text="Trasforma immagini in celle quadrate compatibili con Excel e Google Fogli.").pack(anchor="w", pady=(0, 12))
        content = ttk.Panedwindow(root, orient="horizontal")
        content.pack(fill="both", expand=True)
        controls = ttk.Frame(content, padding=(0, 0, 12, 0), width=330)
        preview = ttk.Frame(content)
        content.add(controls, weight=0)
        content.add(preview, weight=1)

        file_box = ttk.LabelFrame(controls, text="1. Immagine", style="Section.TLabelframe", padding=10)
        file_box.pack(fill="x", pady=(0, 8))
        ttk.Button(file_box, text="Apri immagine...", command=self.open_image).pack(fill="x")
        self.file_label = ttk.Label(file_box, text="Nessuna immagine selezionata", wraplength=290)
        self.file_label.pack(anchor="w", pady=(7, 0))

        size_box = ttk.LabelFrame(controls, text="2. Dimensioni", style="Section.TLabelframe", padding=10)
        size_box.pack(fill="x", pady=8)
        ttk.Label(size_box, text="Larghezza in celle").grid(row=0, column=0, sticky="w")
        self.width_var = tk.IntVar(value=543)
        width_spin = ttk.Spinbox(size_box, from_=16, to=16384, textvariable=self.width_var, width=10, command=self._update_estimate)
        width_spin.grid(row=0, column=1, sticky="e")
        width_spin.bind("<KeyRelease>", lambda _e: self._update_estimate())
        ttk.Label(size_box, text="Ricampionamento").grid(row=1, column=0, sticky="w", pady=(8, 0))
        self.resampling_var = tk.StringVar(value="Lanczos")
        ttk.Combobox(size_box, state="readonly", textvariable=self.resampling_var,
                     values=["Lanczos", "Bicubico", "Bilineare", "Pixel / nearest"], width=18).grid(row=1, column=1, sticky="e", pady=(8, 0))
        self.estimate_label = ttk.Label(size_box, text="Altezza: - | Celle: -")
        self.estimate_label.grid(row=2, column=0, columnspan=2, sticky="w", pady=(8, 0))
        size_box.columnconfigure(0, weight=1)

        color_box = ttk.LabelFrame(controls, text="3. Colori e dithering", style="Section.TLabelframe", padding=10)
        color_box.pack(fill="x", pady=8)
        self.palette_var = tk.StringVar(value="RGB primari")
        palette_combo = ttk.Combobox(color_box, state="readonly", textvariable=self.palette_var,
                                     values=list(BUILTIN_PALETTES) + ["Personalizzata"], width=28)
        palette_combo.pack(fill="x")
        palette_combo.bind("<<ComboboxSelected>>", self._palette_changed)
        self.swatches = ttk.Frame(color_box)
        self.swatches.pack(fill="x", pady=7)
        ttk.Button(color_box, text="Modifica palette...", command=self.edit_palette).pack(fill="x")
        ttk.Label(color_box, text="Dithering").pack(anchor="w", pady=(8, 2))
        self.dither_var = tk.StringVar(value="Floyd-Steinberg serpentino")
        ttk.Combobox(color_box, state="readonly", textvariable=self.dither_var,
                     values=["Nessuno", "Floyd-Steinberg", "Floyd-Steinberg serpentino", "Atkinson", "Bayer 4x4"]).pack(fill="x")
        self._draw_swatches()

        action_box = ttk.LabelFrame(controls, text="4. Conversione", style="Section.TLabelframe", padding=10)
        action_box.pack(fill="x", pady=8)
        self.convert_button = ttk.Button(action_box, text="Genera anteprima", style="Primary.TButton", command=self.start_conversion)
        self.convert_button.pack(fill="x")
        self.progress = ttk.Progressbar(action_box, maximum=100)
        self.progress.pack(fill="x", pady=(9, 3))
        self.status_label = ttk.Label(action_box, text="Pronto")
        self.status_label.pack(anchor="w")

        export_box = ttk.LabelFrame(controls, text="5. Esportazione", style="Section.TLabelframe", padding=10)
        export_box.pack(fill="x", pady=8)
        self.png_button = ttk.Button(export_box, text="Esporta PNG...", command=self.save_png, state="disabled")
        self.png_button.pack(fill="x")
        self.xlsx_button = ttk.Button(export_box, text="Esporta XLSX per Google Fogli...", command=self.save_xlsx, state="disabled")
        self.xlsx_button.pack(fill="x", pady=(6, 0))

        notebook = ttk.Notebook(preview)
        notebook.pack(fill="both", expand=True)
        original_tab = ttk.Frame(notebook, padding=8)
        result_tab = ttk.Frame(notebook, padding=8)
        stats_tab = ttk.Frame(notebook, padding=8)
        notebook.add(original_tab, text="Originale")
        notebook.add(result_tab, text="Anteprima convertita")
        notebook.add(stats_tab, text="Statistiche e legenda")
        self.original_label = ttk.Label(original_tab, text="Apri un'immagine per visualizzarla", anchor="center")
        self.original_label.pack(fill="both", expand=True)
        self.result_label = ttk.Label(result_tab, text="Genera l'anteprima", anchor="center")
        self.result_label.pack(fill="both", expand=True)
        self.stats = ttk.Treeview(stats_tab, columns=("color", "count", "percent"), show="headings")
        self.stats.heading("color", text="Colore")
        self.stats.heading("count", text="Celle")
        self.stats.heading("percent", text="Percentuale")
        self.stats.column("color", width=180, anchor="center")
        self.stats.column("count", width=140, anchor="e")
        self.stats.column("percent", width=140, anchor="e")
        self.stats.pack(fill="both", expand=True)

    def _palette_changed(self, _event=None):
        name = self.palette_var.get()
        if name in BUILTIN_PALETTES:
            self.palette_colors = BUILTIN_PALETTES[name].copy()
            self._draw_swatches()
        elif name == "Personalizzata":
            self.edit_palette()

    def _draw_swatches(self):
        for widget in self.swatches.winfo_children():
            widget.destroy()
        for color in self.palette_colors:
            tk.Label(self.swatches, bg=color, width=4, height=1, relief="solid", borderwidth=1).pack(side="left", padx=2)

    def edit_palette(self):
        window = tk.Toplevel(self)
        window.title("Palette personalizzata")
        window.transient(self)
        window.grab_set()
        frame = ttk.Frame(window, padding=14)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Inserisci codici #RRGGBB separati da virgola:").pack(anchor="w")
        value = tk.StringVar(value=", ".join(self.palette_colors))
        entry = ttk.Entry(frame, textvariable=value, width=55)
        entry.pack(fill="x", pady=8)

        def add_color():
            picked = colorchooser.askcolor(parent=window)[1]
            if picked:
                value.set((value.get() + ", " + picked.upper()).strip(", "))

        def confirm():
            try:
                colors, _ = normalize_palette(value.get().split(","))
                if len(colors) > 64:
                    raise ValueError("Sono ammessi al massimo 64 colori.")
                self.palette_colors = colors
                self.palette_var.set("Personalizzata")
                self._draw_swatches()
                window.destroy()
            except ValueError as exc:
                messagebox.showerror("Palette non valida", str(exc), parent=window)

        buttons = ttk.Frame(frame)
        buttons.pack(fill="x")
        ttk.Button(buttons, text="Scegli colore", command=add_color).pack(side="left")
        ttk.Button(buttons, text="Conferma", command=confirm).pack(side="right")

    def open_image(self):
        path = filedialog.askopenfilename(filetypes=[("Immagini", "*.jpg *.jpeg *.png *.tif *.tiff *.webp *.bmp"), ("Tutti i file", "*.*")])
        if not path:
            return
        try:
            image = Image.open(path)
            image.load()
            self.source_image = image.convert("RGB")
            self.source_path = Path(path)
            self.file_label.config(text=f"{self.source_path.name}\n{self.source_image.width} x {self.source_image.height} px")
            self._show_image(self.source_image, self.original_label, "original")
            self._update_estimate()
            self.result = None
            self.png_button.config(state="disabled")
            self.xlsx_button.config(state="disabled")
        except Exception as exc:
            messagebox.showerror("Immagine non leggibile", str(exc))

    def _show_image(self, image: Image.Image, label: ttk.Label, which: str):
        copy = image.copy()
        copy.thumbnail((820, 620), Image.Resampling.LANCZOS)
        photo = ImageTk.PhotoImage(copy)
        label.config(image=photo, text="")
        if which == "original":
            self.preview_original = photo
        else:
            self.preview_result = photo

    def _update_estimate(self):
        if not self.source_image:
            return
        try:
            width, height = target_size(self.source_image.size, int(self.width_var.get()))
            cells = width * height
            warning = " - ALTO" if cells > 500_000 else ""
            self.estimate_label.config(text=f"Altezza: {height} | Celle: {cells:,}{warning}".replace(",", "."))
        except Exception as exc:
            self.estimate_label.config(text=str(exc))

    def start_conversion(self):
        if not self.source_path:
            messagebox.showwarning("Immagine mancante", "Seleziona prima un'immagine.")
            return
        try:
            colors, _ = normalize_palette(self.palette_colors)
            width = int(self.width_var.get())
            target_size(self.source_image.size, width)
        except Exception as exc:
            messagebox.showerror("Parametri non validi", str(exc))
            return
        self.convert_button.config(state="disabled")
        self.png_button.config(state="disabled")
        self.xlsx_button.config(state="disabled")
        threading.Thread(target=self._conversion_worker, args=(width, colors), daemon=True).start()

    def _progress_event(self, value: int, text: str):
        self.events.put(("progress", value, text))

    def _conversion_worker(self, width, colors):
        try:
            result = convert_image(self.source_path, width, colors, self.dither_var.get(), self.resampling_var.get(), self._progress_event)
            self.events.put(("converted", result))
        except Exception as exc:
            self.events.put(("error", str(exc)))

    def _poll_events(self):
        try:
            while True:
                event = self.events.get_nowait()
                if event[0] == "progress":
                    self.progress["value"] = event[1]
                    self.status_label.config(text=event[2])
                elif event[0] == "converted":
                    self.result = event[1]
                    self._show_image(self.result.image, self.result_label, "result")
                    self._fill_stats()
                    self.convert_button.config(state="normal")
                    self.png_button.config(state="normal")
                    self.xlsx_button.config(state="normal")
                    self.progress["value"] = 100
                    self.status_label.config(text="Anteprima completata")
                elif event[0] == "exported":
                    self.convert_button.config(state="normal")
                    self.png_button.config(state="normal")
                    self.xlsx_button.config(state="normal")
                    messagebox.showinfo("Esportazione completata", event[1])
                elif event[0] == "error":
                    self.convert_button.config(state="normal")
                    self.png_button.config(state="normal" if self.result else "disabled")
                    self.xlsx_button.config(state="normal" if self.result else "disabled")
                    messagebox.showerror("Errore", event[1])
        except queue.Empty:
            pass
        self.after(100, self._poll_events)

    def _fill_stats(self):
        for item in self.stats.get_children():
            self.stats.delete(item)
        total = sum(self.result.counts)
        for color, count in zip(self.result.colors, self.result.counts):
            tag = color
            self.stats.insert("", "end", values=(color, f"{count:,}".replace(",", "."), f"{count/total:.2%}"), tags=(tag,))
            self.stats.tag_configure(tag, background=color, foreground="#FFFFFF" if sum(int(color[i:i+2], 16) for i in (1,3,5)) < 380 else "#000000")

    def save_png(self):
        path = filedialog.asksaveasfilename(defaultextension=".png", filetypes=[("PNG", "*.png")], initialfile="immagine_quantizzata.png")
        if path:
            try:
                export_png(self.result, path)
                messagebox.showinfo("Esportazione completata", f"PNG salvato in:\n{path}")
            except Exception as exc:
                messagebox.showerror("Errore", str(exc))

    def save_xlsx(self):
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", filetypes=[("Excel", "*.xlsx")], initialfile="immagine_pixel_google.xlsx")
        if not path:
            return
        self.convert_button.config(state="disabled")
        self.png_button.config(state="disabled")
        self.xlsx_button.config(state="disabled")

        def worker():
            try:
                export_xlsx(self.result, path, self._progress_event)
                self.events.put(("exported", f"File XLSX salvato in:\n{path}"))
            except Exception as exc:
                self.events.put(("error", str(exc)))
        threading.Thread(target=worker, daemon=True).start()


def run():
    PixelSheetApp().mainloop()

