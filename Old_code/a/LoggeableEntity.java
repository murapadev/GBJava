package a;

import java.lang.reflect.Field;

public class LoggeableEntity {
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		try {
			builder.append(this.getClass().getSimpleName() + " [");
			Field[] oa;
			for (Field f : (oa = this.getClass().getDeclaredFields())) {
				f.setAccessible(true);
				builder.append( f.getName() + ": " + f.get(this).toString() + (!f.equals(oa[oa.length-1]) ? ", " : ""));
			}
		} 
		catch (Exception e) {}
		finally {
			return builder.append("]").toString();
		}
	}
	
}
