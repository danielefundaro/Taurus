package com.fnd.taurus.model;

import com.fnd.taurus.entity.CommonFields;
import lombok.Getter;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;
import java.util.function.Function;

public class GenericSpecificationsBuilder<C extends CommonFields> {

    @Getter
    private final List<QueryItem> params = new ArrayList<>();

    public Specification<C> build(Deque<?> postFixedExprStack, Function<QueryItem, Specification<C>> converter) {
        Deque<Specification<C>> specStack = new LinkedList<>();
        Collections.reverse((List<?>) postFixedExprStack);

        while (!postFixedExprStack.isEmpty()) {
            Object mayBeOperand = postFixedExprStack.pop();

            if (!(mayBeOperand instanceof String)) {
                specStack.push(converter.apply((QueryItem) mayBeOperand));
            } else {
                Specification<C> operand1 = specStack.pop();
                Specification<C> operand2 = specStack.pop();

                if (mayBeOperand.equals(QuerySyntax.AND_OPERATOR))
                    specStack.push(Specification.where(operand1).and(operand2));
                else if (mayBeOperand.equals(QuerySyntax.OR_OPERATOR))
                    specStack.push(Specification.where(operand1).or(operand2));
            }
        }

        return specStack.pop();
    }
}
