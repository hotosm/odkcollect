package org.odk.collect.android.widgets.base;

import static junit.framework.Assert.assertTrue;

import android.text.method.PasswordTransformationMethod;
import android.view.View;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.data.IAnswerData;
import org.junit.Test;
import org.mockito.Mock;
import org.odk.collect.android.R;
import org.odk.collect.android.support.WidgetTestActivity;
import org.odk.collect.android.utilities.Appearances;
import org.odk.collect.android.widgets.StringWidget;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

/**
 * @author James Knight
 */
public abstract class GeneralStringWidgetTest<W extends StringWidget, A extends IAnswerData>
        extends QuestionWidgetTest<W, A> {

    @Mock
    QuestionDef questionDef;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(formEntryPrompt.getQuestion()).thenReturn(questionDef);
    }

    @Override
    public void callingClearShouldRemoveTheExistingAnswer() {
        super.callingClearShouldRemoveTheExistingAnswer();

        W widget = getSpyWidget();
        assertEquals(widget.getAnswerText(), "");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getAnswerShouldReturnExistingAnswerIfPromptHasExistingAnswer() {
        super.getAnswerShouldReturnExistingAnswerIfPromptHasExistingAnswer();

        W widget = getSpyWidget();
        IAnswerData answer = widget.getAnswer();

        assertEquals(widget.getAnswerText(), answer.getDisplayText());
    }

    @Test
    public void getAnswerShouldReturnNewAnswerWhenTextFieldIsUpdated() {
        // Make sure it starts null:
        super.getAnswerShouldReturnNullIfPromptDoesNotHaveExistingAnswer();

        W widget = getSpyWidget();
        IAnswerData answer = getNextAnswer();

        when(widget.getAnswerText()).thenReturn(answer.getDisplayText());
        IAnswerData computedAnswer = widget.getAnswer();

        assertEquals(answer.getDisplayText(), computedAnswer.getDisplayText());
    }

    @Test
    public void usingReadOnlyOptionShouldMakeAllClickableElementsDisabled() {
        when(formEntryPrompt.isReadOnly()).thenReturn(true);

        assertThat(getSpyWidget().widgetAnswerText.isEditableState(), is(false));
    }

    @Test
    public void whenReadOnlyOverrideOptionIsUsed_shouldAllClickableElementsBeDisabled() {
        readOnlyOverride = true;
        when(formEntryPrompt.isReadOnly()).thenReturn(false);

        assertThat(getSpyWidget().widgetAnswerText.isEditableState(), is(false));
    }

    /**
     * Unlike other widgets, String widgets that contain EditText should not be registered to
     * context menu as a whole because the Clipboard menu would be broken.
     *
     * https://github.com/getodk/collect/pull/4860
     */
    @Test
    public void widgetShouldBeRegisteredForContextMenu() {
        StringWidget widget = createWidget();
        List<View> viewsRegisterForContextMenu = ((WidgetTestActivity) activity).viewsRegisterForContextMenu;

        assertThat(viewsRegisterForContextMenu.size(), is(3));

        assertTrue(viewsRegisterForContextMenu.contains(widget.findViewWithTag(R.id.question_label)));
        assertTrue(viewsRegisterForContextMenu.contains(widget.findViewWithTag(R.id.help_text)));
        assertTrue(viewsRegisterForContextMenu.contains(widget.findViewWithTag(R.id.error_message_container)));

        assertThat(viewsRegisterForContextMenu.get(0).getId(), is(widget.getId()));
        assertThat(viewsRegisterForContextMenu.get(1).getId(), is(widget.getId()));
    }

    @Test
    public void answersShouldNotBeMaskedIfMaskedAppearanceIsNotUsed() {
        assertThat(getSpyWidget().widgetAnswerText.getBinding().editText.getTransformationMethod(), is(not(instanceOf(PasswordTransformationMethod.class))));
        assertThat(getSpyWidget().widgetAnswerText.getBinding().textView.getTransformationMethod(), is(not(instanceOf(PasswordTransformationMethod.class))));
    }

    @Test
    public void answersShouldBeMaskedIfMaskedAppearanceIsUsed() {
        when(formEntryPrompt.getAppearanceHint()).thenReturn(Appearances.MASKED);

        assertThat(getSpyWidget().widgetAnswerText.getBinding().editText.getTransformationMethod(), is(instanceOf(PasswordTransformationMethod.class)));
        assertThat(getSpyWidget().widgetAnswerText.getBinding().textView.getTransformationMethod(), is(instanceOf(PasswordTransformationMethod.class)));
    }

    @Test
    public abstract void verifyInputType();

    @Test
    public abstract void verifyInputTypeWithMaskedAppearance();
}