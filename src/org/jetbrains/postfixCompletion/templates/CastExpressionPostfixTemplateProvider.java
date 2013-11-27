package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.ExpectedTypeMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.CommonUtils;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.ExpressionPostfixLookupElement;

import java.util.List;

// todo: when invoked inside code fragment - insert ((T) expr)?
// todo: also when can be expression-statements?

@TemplateProvider(
  templateName = "cast",
  description = "Surrounds expression with cast",
  example = "(SomeType) expr",
  worksInsideFragments = true)
public final class CastExpressionPostfixTemplateProvider extends PostfixTemplateProvider {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    if (!context.executionContext.isForceMode) return;

    PrefixExpressionContext bestContext = context.outerExpression();
    List<PrefixExpressionContext> expressions = context.expressions();

    for (int index = expressions.size() - 1; index >= 0; index--) {
      PrefixExpressionContext expressionContext = expressions.get(index);
      if (CommonUtils.isNiceExpression(expressionContext.expression)) {
        bestContext = expressionContext;
        break;
      }
    }

    consumer.add(new CastLookupElement(bestContext));
  }

  static final class CastLookupElement extends ExpressionPostfixLookupElement<PsiTypeCastExpression> {
    public CastLookupElement(@NotNull PrefixExpressionContext context) {
      super("cast", context);
    }

    @NotNull @Override protected PsiTypeCastExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

      PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression) factory.createExpressionFromText("(T) expr", context);

      PsiExpression operand = typeCastExpression.getOperand();
      assert (operand != null) : "operand != null";
      operand.replace(expression);

      return typeCastExpression;
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiTypeCastExpression expression) {

      SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      final SmartPsiElementPointer<PsiTypeCastExpression> pointer =
        pointerManager.createSmartPsiElementPointer(expression);

      final Runnable runnable = new Runnable() {
        @Override public void run() {
          PsiTypeCastExpression castExpression = pointer.getElement();
          if (castExpression == null) return;

          TemplateBuilderImpl builder = new TemplateBuilderImpl(castExpression);

          PsiTypeElement castType = castExpression.getCastType();
          assert (castType != null) : "castType != null";

          builder.replaceElement(castType, new MacroCallNode(new ExpectedTypeMacro()), true);
          builder.setEndVariableAfter(castExpression);

          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          editor.getCaretModel().moveToOffset(
            castExpression.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(context.getProject());
          manager.startTemplate(editor, template);
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }
  }
}