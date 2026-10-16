# JJKConverter.exe: the converter in a window. Pick a recording, name the move, press Convert; it runs r6_batch.py
# (every take found, converted, the main one redone with the others as guides) and shows its progress.
import sys, os, re, queue, threading, subprocess
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

FROZEN = getattr(sys, 'frozen', False)
ROOT = os.path.dirname(sys.executable) if FROZEN else \
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, 'tools')
NO_WINDOW = 0x08000000 if os.name == 'nt' else 0  # CREATE_NO_WINDOW: no black console boxes


def worker_cmd(script, *args):
    if FROZEN:
        exe = os.path.join(ROOT, 'jjk_worker.exe' if os.name == 'nt' else 'jjk_worker')
        return [exe, os.path.join(TOOLS, script), *args]
    return [sys.executable, os.path.join(TOOLS, script), *args]


def open_folder(path):
    os.makedirs(path, exist_ok=True)
    if os.name == 'nt':
        os.startfile(path)
    elif sys.platform == 'darwin':
        subprocess.Popen(['open', path])
    else:
        subprocess.Popen(['xdg-open', path])


class App:
    def __init__(self, root):
        self.root, self.proc, self.lines = root, None, queue.Queue()
        root.title('JJK R6 animation converter')
        root.geometry('760x560')
        f = ttk.Frame(root, padding=10)
        f.pack(fill='both', expand=True)
        f.columnconfigure(1, weight=1)
        self.video, self.name = tk.StringVar(), tk.StringVar()
        self.main, self.hint, self.viewer = tk.StringVar(), tk.StringVar(), tk.BooleanVar(value=False)
        self.fresh = tk.BooleanVar(value=False)
        row = 0
        ttk.Label(f, text='Recording').grid(row=row, column=0, sticky='w')
        ttk.Entry(f, textvariable=self.video).grid(row=row, column=1, sticky='ew', padx=6)
        ttk.Button(f, text='Browse...', command=self.browse).grid(row=row, column=2)
        row += 1
        ttk.Label(f, text='Move name').grid(row=row, column=0, sticky='w', pady=4)
        ttk.Entry(f, textvariable=self.name).grid(row=row, column=1, sticky='ew', padx=6)
        row += 1
        ttk.Label(f, text='Main take (optional)').grid(row=row, column=0, sticky='w')
        ttk.Entry(f, textvariable=self.main, width=6).grid(row=row, column=1, sticky='w', padx=6)
        row += 1
        ttk.Label(f, text='Hints (optional)').grid(row=row, column=0, sticky='w', pady=4)
        ttk.Entry(f, textvariable=self.hint).grid(row=row, column=1, sticky='ew', padx=6)
        ttk.Label(f, text='e.g. 364-367:rightThigh:forward', foreground='gray').grid(row=row, column=2, sticky='w')
        row += 1
        opts = ttk.Frame(f)
        opts.grid(row=row, column=1, sticky='w', padx=6)
        ttk.Checkbutton(opts, text='Build the viewer page', variable=self.viewer).pack(side='left')
        ttk.Checkbutton(opts, text='Start over (ignore earlier work)', variable=self.fresh).pack(side='left', padx=12)
        row += 1
        bar = ttk.Frame(f)
        bar.grid(row=row, column=0, columnspan=3, sticky='w', pady=8)
        self.b_takes = ttk.Button(bar, text='Find takes', command=self.find_takes)
        self.b_takes.pack(side='left')
        self.b_go = ttk.Button(bar, text='Convert', command=self.convert)
        self.b_go.pack(side='left', padx=6)
        self.b_stop = ttk.Button(bar, text='Stop', command=self.stop, state='disabled')
        self.b_stop.pack(side='left')
        ttk.Button(bar, text='Open results', command=lambda: open_folder(os.path.join(ROOT, 'build', 'fit'))).pack(
            side='left', padx=6)
        ttk.Button(bar, text='Open logs', command=self.open_logs).pack(side='left')
        row += 1
        self.status = tk.StringVar(value='Pick a recording to start.')
        ttk.Label(f, textvariable=self.status).grid(row=row, column=0, columnspan=3, sticky='w')
        row += 1
        f.rowconfigure(row, weight=1)
        self.log = tk.Text(f, height=20, wrap='word', state='disabled')
        self.log.grid(row=row, column=0, columnspan=3, sticky='nsew')
        sb = ttk.Scrollbar(f, command=self.log.yview)
        sb.grid(row=row, column=3, sticky='ns')
        self.log['yscrollcommand'] = sb.set
        root.protocol('WM_DELETE_WINDOW', self.close)
        root.after(200, self.pump)

    def browse(self):
        p = filedialog.askopenfilename(title='Recording', filetypes=[('Videos', '*.mp4 *.mov *.mkv *.avi *.webm'),
                                                                     ('All files', '*.*')])
        if p:
            self.video.set(p)
            if not self.name.get():
                self.name.set(re.sub(r'[^a-z0-9_]+', '_', os.path.splitext(os.path.basename(p))[0].lower()).strip('_'))

    def say(self, text):
        self.log['state'] = 'normal'
        self.log.insert('end', text if text.endswith('\n') else text + '\n')
        self.log.see('end')
        self.log['state'] = 'disabled'

    def check(self, need_name=True):
        v = self.video.get().strip()
        if not v or not os.path.isfile(v):
            messagebox.showerror('Recording', 'Pick the recording first.')
            return None
        n = self.name.get().strip()
        if need_name and not re.fullmatch(r'[a-z0-9_]+', n or ''):
            messagebox.showerror('Move name', 'Use lowercase letters, digits and _ only, e.g. cursed_strikes.')
            return None
        m = self.main.get().strip()
        if m and not m.isdigit():
            messagebox.showerror('Main take', 'The main take is a number from the take list (0, 1, 2...), or empty.')
            return None
        return v, n, m

    def run(self, cmd, label, done):
        self.log['state'] = 'normal'
        self.log.delete('1.0', 'end')
        self.log['state'] = 'disabled'
        self.status.set(label)
        self.b_go['state'] = self.b_takes['state'] = 'disabled'
        self.b_stop['state'] = 'normal'
        self.done = done
        self.proc = subprocess.Popen(cmd, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     stdin=subprocess.DEVNULL, creationflags=NO_WINDOW)

        def read(p):
            for raw in iter(p.stdout.readline, b''):
                self.lines.put(raw.decode('utf-8', 'replace').rstrip('\r\n'))
            self.lines.put((p.wait(),))
        threading.Thread(target=read, args=(self.proc,), daemon=True).start()

    def pump(self):
        try:
            while True:
                x = self.lines.get_nowait()
                if isinstance(x, tuple):
                    self.proc = None
                    self.b_go['state'] = self.b_takes['state'] = 'normal'
                    self.b_stop['state'] = 'disabled'
                    self.done(x[0])
                else:
                    self.say(x)
                    if re.match(r'take \d+|main take|done:|\d/\d ', x):
                        self.status.set(x)
        except queue.Empty:
            pass
        self.root.after(200, self.pump)

    def find_takes(self):
        c = self.check(need_name=False)
        if c:
            self.run(worker_cmd('r6_takes.py', c[0]), 'Looking for takes...',
                     lambda rc: self.status.set('Takes listed below.' if rc == 0 else 'Could not read the recording.'))

    def convert(self):
        c = self.check()
        if not c:
            return
        v, n, m = c
        args = [v, n]
        if m:
            args += ['--main', m]
        if self.hint.get().strip():
            args += ['--hint', self.hint.get().strip()]
        if self.viewer.get():
            args.append('--viewer')
        if self.fresh.get():
            args.append('--fresh')
        clip = os.path.join(ROOT, 'build', 'fit', n, 'clip.json')

        def done(rc):
            if rc == 0:
                self.status.set(f'Done: {clip}')
                messagebox.showinfo('Done', f'The clip is ready:\n{clip}')
            elif rc is not None and rc < 0 or getattr(self, 'stopped', False):
                self.status.set('Stopped. Press Convert to carry on where it stopped.')
            else:
                self.status.set('It stopped with an error: see the log below and "Open logs".')
            self.stopped = False
        self.stopped = False
        self.run(worker_cmd('r6_batch.py', *args), 'Converting... (this takes a while; it can be stopped and resumed)',
                 done)

    def stop(self):
        if self.proc:
            self.stopped = True
            if os.name == 'nt':  # the whole tree: the batch, its conversions and their worker processes
                subprocess.run(['taskkill', '/F', '/T', '/PID', str(self.proc.pid)], creationflags=NO_WINDOW,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                self.proc.kill()

    def open_logs(self):
        n = self.name.get().strip()
        open_folder(os.path.join(ROOT, 'build', 'convert', n) if n else os.path.join(ROOT, 'build', 'convert'))

    def close(self):
        if self.proc and not messagebox.askyesno('Quit', 'A conversion is running. Stop it and quit? '
                                                 '(Convert again later to resume.)'):
            return
        self.stop()
        self.root.destroy()


def main():
    root = tk.Tk()
    App(root)
    root.mainloop()


if __name__ == '__main__':
    main()
