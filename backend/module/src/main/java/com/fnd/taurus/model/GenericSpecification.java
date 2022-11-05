package com.fnd.taurus.model;

import com.fnd.taurus.entity.CommonFields;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import javax.persistence.criteria.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class GenericSpecification<C extends CommonFields> implements Specification<C> {
    private final static String[] DATE_FORMATS = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd", "yyyy/MM/dd-HH.mm.ss", "yyyy/MM/dd-HH.mm", "yyyy/MM/dd"};

    protected QueryItem item;

    public GenericSpecification(final QueryItem item) {
        super();
        this.item = item;
    }

    @Override
    public Predicate toPredicate(@Nullable Root<C> root, CriteriaQuery<?> query, @Nullable CriteriaBuilder criteriaBuilder) {
        // get the path
        Path<?> path = getPath(root, item.getKey());
        // get the value
        Object value = null;
        // set distinct
        query.distinct(true);

        try {
            value = getFixedValue(path, item.getValue());
        } catch (Exception e) {
            log.warn(e.getMessage());
        }

        return processPredicate(criteriaBuilder, path, value);
    }

    protected Predicate processPredicate(CriteriaBuilder builder, Path path, Object value) {
        switch (item.getOperation()) {
            case EQUALITY:
                if (value instanceof String && value.equals("IS_NULL")) {
                    return builder.isNull(path);
                } else if (value instanceof String && value.equals("IS_NOT_NULL")) {
                    return builder.isNotNull(path);
                } else if (value instanceof String && value.equals("IS_FALSE")) { //This will return all undeleted and NULL records
                    Predicate falseCondition = builder.equal(path, false);
                    Predicate nullCondition = builder.isNull(path);

                    return builder.or(falseCondition, nullCondition);
                } else {
                    return builder.equal(path, value);
                }
            case NEGATION:
                return builder.notEqual(path, value);
            case GREATER_THAN:
                if (value instanceof Date) {
                    return builder.greaterThan((Path<Date>) path, (Date) value);
                } else {
                    return builder.greaterThan(path, value.toString());
                }
            case LESS_THAN:
                if (value instanceof Date) {
                    return builder.lessThan((Path<Date>) path, (Date) value);
                } else {
                    return builder.lessThan(path, value.toString());
                }
            case GREATER_EQUAL_THAN:
                if (value instanceof Date) {
                    return builder.greaterThanOrEqualTo((Path<Date>) path, (Date) value);
                } else {
                    return builder.greaterThanOrEqualTo(path, value.toString());
                }
            case LESS_EQUAL_THAN:
                if (value instanceof Date) {
                    return builder.lessThanOrEqualTo((Path<Date>) path, (Date) value);
                } else {
                    return builder.lessThanOrEqualTo(path, value.toString());
                }
            case LIKE:
                return builder.like(path, value.toString());
            case STARTS_WITH:
                return builder.like(path, value + "%");
            case ENDS_WITH:
                return builder.like(path, "%" + value);
            case CONTAINS:
                return builder.like(path, "%" + value + "%");
            default:
                return null;
        }
    }

    protected Object getFixedValue(Path<?> path, Object value) throws Exception {
        Object res = value;
        Class<?> type;
        SimpleDateFormat formatter;
        Date date;
        int i = 0;
        String format;

        if (path != null) {
            type = path.getJavaType();

            // manage bool value
            if (type.equals(Boolean.class)) {
                if (value instanceof String) {
                    if (((String) value).equalsIgnoreCase("true")) {
                        res = true;
                    } else if (((String) value).equalsIgnoreCase("false")) {
                        res = false;
                    }
                }
            }

            // manage enum value
            else if (type.isEnum()) {
                if (value instanceof String) {
                    for (Object enumConstant : type.getEnumConstants()) {
                        if (((Enum<?>) enumConstant).name().equalsIgnoreCase(((String) value))) {
                            res = enumConstant;
                            break;
                        }
                    }
                }
            }

            // manage date value
            else if (type.equals(Date.class)) {
                date = null;

                do {
                    format = DATE_FORMATS[i++];
                    formatter = new SimpleDateFormat(format);
                    try {
                        date = formatter.parse((String) value);
                    } catch (ParseException ignored) {
                    }
                } while (date == null && i < DATE_FORMATS.length);

                if (date != null) {
                    res = date;
                } else {
                    throw new Exception("Date not valid");
                }
            }
        }

        return res;
    }

    // recovery path from pathSting
    // key has be like "address.person.attribute_name" or "attribute_name"
    protected Path<?> getPath(Root<C> root, String pathSting) {
        Path<?> path = null;
        Join<?, ?> join = null;
        int dot1;
        String localPathString;

        try {
            do {
                dot1 = pathSting.indexOf('.');
                if (dot1 != -1) {
                    // step
                    localPathString = pathSting.substring(0, dot1);
                    pathSting = pathSting.substring(dot1 + 1);
                    if (join != null) {
                        join = join.join(localPathString);
                    } else {
                        join = root.join(localPathString);
                    }
                } else {
                    // base case( only attribute )
                    if (join != null) {
                        path = join.get(pathSting);
                    } else {
                        path = root.get(pathSting);
                    }
                }
            } while (dot1 != -1);
        } catch (Exception e) {
            log.error(String.format("Error into query path \"%s\": %s", pathSting, e.getMessage()));
        }

        return path;
    }
}