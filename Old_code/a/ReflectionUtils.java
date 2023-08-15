package a;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReflectionUtils {
	
	public static final SecurityManagerL SecurityManager= new SecurityManagerL();

	public static <T,R> R getFieldValue(T object, String name) {
		R temp = null;
		
		try {
			Field field = object.getClass().getDeclaredField(name);
			boolean accesible = field.isAccessible();
			if (field != null) {
				field.setAccessible(true);
				temp = (R) field.get(object);
			}
			field.setAccessible(accesible);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		} finally {
			return temp;
		}
	}
	
	public static <T,R> R getFieldValueIgnoreCase(T object, String name) {
		R temp = null;
		
		try {
			Field field = null;
			for (Field f : object.getClass().getDeclaredFields()) {
				if (f.getName().toLowerCase().equals(name.toLowerCase())) {
					field = f;
					break;
				}
			} 
			boolean accesible = field.isAccessible();
			if (field != null) {
				field.setAccessible(true);
				temp = (R) field.get(object);
			}
			field.setAccessible(accesible);
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		} finally {
			return temp;
		}
	}
	
	public static <E, T> E modifyAttribute(E object, String fieldName, T fieldValue) {
		try {
			Field field = object.getClass().getDeclaredField(fieldName);
			boolean accesible = field.isAccessible();
			if (field != null) {
				field.setAccessible(true);
				field.set(object, fieldValue);
			}
			field.setAccessible(accesible);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		} finally {
			return object;
		}
	}
	
	public static <E, T> E modifyAttributeIgnoreCase(E object, String fieldName, T fieldValue) {
		try {
			Field field = null;
			for (Field f : object.getClass().getDeclaredFields()) {
				if (f.getName().toLowerCase().equals(fieldName.toLowerCase())) {
					field = f;
					break;
				}
			} 
			
			// to help debugging by raising NoSuchFieldException
			if (field == null) {
				field = object.getClass().getDeclaredField(fieldName);
			}
			
			boolean accesible = field.isAccessible();
			if (field != null) {
				field.setAccessible(true);
				field.set(object, fieldValue);
			}
			field.setAccessible(accesible);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		} finally {
			return object;
		}
	}
	
	public static <E> E newInstance(Class clazz, Object... attributes) {
		E e = null;
		
		try {
			Object[] parameterTypes = Arrays.asList(new Class[attributes.length]).stream()
					.map(new Function<Object, Class>() {

						@Override
						public Class apply(Object t) {
							return t.getClass();
						}
						
					})
					.collect(Collectors.toList())
					.toArray();
			Constructor constructor = clazz.getDeclaredConstructor((Class[]) parameterTypes);
			
			if (constructor != null) {
				e = (E) constructor.newInstance(attributes);
			} else {
				e = (E) e.getClass().newInstance();
			}
			
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException exception) {
			// TODO Auto-generated catch block
			exception.printStackTrace();
		}
		
		return e;
	}
	
	public String getCallerClassName(int callStackDepth) {
		return SecurityManager.getCallerClassName(callStackDepth);
	}
	
	public static class SecurityManagerL extends SecurityManager {
        public String getCallerClassName(int callStackDepth) {
            return getClassContext()[callStackDepth].getName();
        }
    }
}
