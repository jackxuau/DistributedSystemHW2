package
        utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class JsonHelper {

    public static String toJson(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return "null";
        }

        Class<?> objClass = obj.getClass();

        // Check if it's a primitive type, String, or wrapper class
        if (isPrimitiveOrWrapper(objClass) || objClass == String.class) {
            return "\"" + obj.toString() + "\"";
        }

        // Handle Collections like List or Set
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }

        // Handle Maps
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey().toString()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        // Handle Arrays
        if (objClass.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                Object arrayElement = Array.get(obj, i);
                sb.append(toJson(arrayElement));
            }
            sb.append("]");
            return sb.toString();
        }

        // Handle custom objects
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Field[] fields = objClass.getDeclaredFields();
        boolean first = true;

        for (Field field : fields) {
            field.setAccessible(true);
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(field.getName()).append("\":");
            Object value = field.get(obj);
            sb.append(toJson(value));
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class || clazz == Byte.class ||
                clazz == Character.class || clazz == Double.class ||
                clazz == Float.class || clazz == Integer.class ||
                clazz == Long.class || clazz == Short.class;
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        if (json == null || json.equals("null")) {
            return null;
        }

        json = json.trim();

        if (isPrimitiveOrWrapper(clazz) || clazz == String.class) {
            return parsePrimitive(json, clazz);
        }

        if (clazz.isArray()) {
            return parseArray(json, clazz);
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            // Handle collection types (e.g., List, Set)
            throw new UnsupportedOperationException("Collection types are not supported in this example.");
        }

        if (Map.class.isAssignableFrom(clazz)) {
            // Handle map types (e.g., HashMap)
            throw new UnsupportedOperationException("Map types are not supported in this example.");
        }

        if (json.startsWith("{") && json.endsWith("}")) {
            return parseObject(json, clazz);
        }

        throw new IllegalArgumentException("Unsupported JSON format or type: " + json);
    }

    private static <T> T parseObject(String json, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        T instance = clazz.newInstance();
        json = json.substring(1, json.length() - 1); // Remove curly braces

        // Use regex to match "key":"value" pairs
        String[] fields = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String field : fields) {
            String[] keyValue = field.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (keyValue.length != 2) {
                throw new IllegalArgumentException("Invalid JSON format: " + field);
            }

            String key = keyValue[0].trim().replace("\"", "");
            String value = keyValue[1].trim();

            try {
                Field classField = clazz.getDeclaredField(key);
                classField.setAccessible(true);
                Object parsedValue = fromJson(value, classField.getType());
                classField.set(instance, parsedValue);
            } catch (NoSuchFieldException e) {
                System.out.println("Field not found: " + key);
            }
        }

        return instance;
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseArray(String json, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        json = json.substring(1, json.length() - 1); // Remove square brackets
        String[] elements = json.split(",");

        Class<?> componentType = clazz.getComponentType();
        Object array = Array.newInstance(componentType, elements.length);

        for (int i = 0; i < elements.length; i++) {
            String element = elements[i].trim();
            Array.set(array, i, fromJson(element, componentType));
        }

        return (T) array;
    }

    @SuppressWarnings("unchecked")
    private static <T> T parsePrimitive(String value, Class<T> clazz) {
        if (clazz == int.class || clazz == Integer.class) {
            return (T) Integer.valueOf(value);
        } else if (clazz == long.class || clazz == Long.class) {
            return (T) Long.valueOf(value);
        } else if (clazz == double.class || clazz == Double.class) {
            return (T) Double.valueOf(value);
        } else if (clazz == boolean.class || clazz == Boolean.class) {
            return (T) Boolean.valueOf(value);
        } else if (clazz == String.class) {
            return (T) value.replace("\"", "");
        } else if (clazz == float.class || clazz == Float.class) {
            return (T) Float.valueOf(value);
        } else if (clazz == short.class || clazz == Short.class) {
            return (T) Short.valueOf(value);
        } else if (clazz == byte.class || clazz == Byte.class) {
            return (T) Byte.valueOf(value);
        } else if (clazz == char.class || clazz == Character.class) {
            return (T) Character.valueOf(value.replace("\"", "").charAt(0));
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + clazz);
    }

}

