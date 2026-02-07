package org.tvrenamer.view;

import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.TableItem;

public class ComboField extends TextField {

    @SuppressWarnings("SameParameterValue")
    ComboField(final String name, final String label) {
        super(Field.Type.COMBO, name, label);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private String itemDestDisplayedText(final TableItem item) {
        synchronized (item) {
            final Object data = item.getData();
            if (data instanceof Combo combo) {
                final int selected = combo.getSelectionIndex();
                final String[] options = combo.getItems();
                return options[selected];
            }
            // For non-Combo controls (e.g., Link) or null data, fall back to cell text.
            return getCellText(item);
        }
    }

    @Override
    public String getItemTextValue(final TableItem item) {
        return itemDestDisplayedText(item);
    }
}
