package javarepl.completion;

import com.googlecode.totallylazy.*;
import javarepl.Evaluator;
import totallyreflective.ClassReflection;
import totallyreflective.MemberReflection;
import totallyreflective.MethodReflection;

import static com.googlecode.totallylazy.Characters.characters;
import static com.googlecode.totallylazy.Option.none;
import static com.googlecode.totallylazy.Option.some;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Predicates.not;
import static com.googlecode.totallylazy.Strings.startsWith;
import static com.googlecode.totallylazy.numbers.Numbers.maximum;
import static totallyreflective.ClassReflections.reflectionOf;
import static totallyreflective.MemberReflections.*;

public class StaticMemberCompleter extends Completer {

    private final Evaluator evaluator;

    public StaticMemberCompleter(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    private Mapper<Character, Integer> lastIndexOf(final String string) {
        return new Mapper<Character, Integer>() {
            public Integer call(Character character) throws Exception {
                return string.lastIndexOf(character);
            }
        };
    }

    public CompletionResult call(String expression) throws Exception {
        final int lastSeparator = lastIndexOfSeparator(expression) + 1;
        final String packagePart = expression.substring(lastSeparator);

        Option<Pair<Class<?>, String>> completion = completionFor(packagePart);

        if (!completion.isEmpty()) {
            Sequence<String> candidates = reflectionOf(completion.get().first())
                    .declaredMembers()
                    .filter(isStatic().and(isPublic()).and(not(isSynthetic())))
                    .map(candidateName())
                    .unique()
                    .filter(startsWith(completion.get().second()));

            final int beginIndex = packagePart.lastIndexOf('.') + 1;
            return new CompletionResult(expression, lastSeparator + beginIndex, candidates);
        } else {
            return new CompletionResult(expression, 0, Sequences.empty(String.class));
        }
    }

    private Mapper<MemberReflection, String> candidateName() {
        return new Mapper<MemberReflection, String>() {
            public String call(MemberReflection memberReflection) throws Exception {
                return new match<MemberReflection, String>() {
                    String value(MethodReflection expr) {
                        return expr.name() + "(";
                    }

                    String value(ClassReflection expr) {
                        return expr.member().getSimpleName();
                    }

                    String value(MemberReflection expr) {
                        return expr.name();
                    }
                }.apply(memberReflection).get();
            }
        };
    }

    private int lastIndexOfSeparator(String expression) {
        return characters("({[,+-\\*>=<&%;!~ ")
                .map(lastIndexOf(expression))
                .reduce(maximum())
                .intValue();
    }


    private Option<Pair<Class<?>, String>> completionFor(String expression) {
        Option<Pair<String, Sequence<String>>> parsedClass = parseExpression(pair(expression, Sequences.empty(String.class)));

        if (!parsedClass.isEmpty() && !parsedClass.get().second().isEmpty()) {
            return some(Pair.<Class<?>, String>pair(
                    evaluator.classFrom(parsedClass.get().first()).get(),
                    parsedClass.get().second().toString(".").trim()));
        } else {
            return none();
        }
    }


    private Option<Pair<String, Sequence<String>>> parseExpression(Pair<String, Sequence<String>> expression) {
        Option<Class> expressionClass = evaluator.classFrom(expression.first());

        if (!expressionClass.isEmpty()) {
            return some(expression);
        }

        if (expression.first().contains(".")) {
            final String packagePart = expression.first().substring(0, expression.first().lastIndexOf("."));
            final String classPart = expression.first().substring(expression.first().lastIndexOf(".") + 1);

            return parseExpression(pair(packagePart, expression.second().cons(classPart)));
        }

        return Option.none();

    }
}