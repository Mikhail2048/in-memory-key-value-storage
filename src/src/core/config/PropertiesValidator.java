package src.core.config;

public class PropertiesValidator {

    public void checkValidIntegerElseExit(String property, String propertyValue) {
        try {
            Integer.parseInt(propertyValue);
        } catch (NumberFormatException e) {
            System.out.printf("Fatal error: Unable to parse property '%s' - the value must be a valid integer number, but provided : '%s'%n", property, propertyValue);
            System.exit(-1);
        }
    }
}
