Behaviour.specify("#credentials-add-submit", 'credentials-dialog-add', 0, function (e) {
    e.onclick = (_) => window.credentials.addSubmit(e);
});

Behaviour.specify("#credentials-add-abort", 'credentials-dialog-abort', 0, function (e) {
    e.onclick = (_) => window.credentials.dialog.style.display = "none";
});