// Snapshot-based undo/redo. Snapshots are JSON strings of the editable document (model minus the
// texture data, animations, active animation), which keeps it simple and reliable.
export class History {
  constructor(limit = 120) { this.limit = limit; this.undoStack = []; this.redoStack = []; }
  push(snapshot, label) {
    const top = this.undoStack[this.undoStack.length - 1];
    if (top && top.s === snapshot) return;
    this.undoStack.push({ s: snapshot, label });
    if (this.undoStack.length > this.limit) this.undoStack.shift();
    this.redoStack.length = 0;
  }
  undo(current) {
    const e = this.undoStack.pop();
    if (!e) return null;
    this.redoStack.push({ s: current, label: e.label });
    return e;
  }
  redo(current) {
    const e = this.redoStack.pop();
    if (!e) return null;
    this.undoStack.push({ s: current, label: e.label });
    return e;
  }
  clear() { this.undoStack.length = 0; this.redoStack.length = 0; }
  get canUndo() { return this.undoStack.length > 0; }
  get canRedo() { return this.redoStack.length > 0; }
}
