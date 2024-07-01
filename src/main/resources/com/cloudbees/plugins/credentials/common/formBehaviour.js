Behaviour.specify(".required-for-submit", 'required-for-submit', -99, function(requiredField) {
    const saveButton = requiredField.closest("form").querySelector('[name="Submit"]');
    function updateSave() {
      const state = requiredField.value.length === 0;
      saveButton.disabled = state;
    }
    requiredField.addEventListener('input', updateSave);
    updateSave(saveButton);
});

Behaviour.specify(".autofocus", "autofocus", 0, function(el) {
    el.focus();
});