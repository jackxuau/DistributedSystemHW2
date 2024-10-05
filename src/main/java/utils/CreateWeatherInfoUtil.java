package utils;

import pojo.WeatherPojo;

import java.util.Random;

public class CreateWeatherInfoUtil {

    /**
     * Creates a mock WeatherInfoWrapperDTO object with randomly generated values.
     *
     * @return WeatherInfoWrapperDTO with mock weather data
     */
    public static WeatherPojo createMockWeatherInfo() {
        Random random = new Random();
        // Generate a random ID in the range of IDS60000 - IDS60020, reduce range to create key conflict
        String id = "IDS" + (60000 + random.nextInt(2000));
        return createMockWeatherInfo(id);
    }

    /**
     * Creates a mock WeatherInfoWrapperDTO object with given id
     *
     * @return WeatherInfoWrapperDTO with mock weather data
     */
    public static WeatherPojo createMockWeatherInfo(String id) {
        Random random = new Random();

        // Generate a random name for the location
        String name = "Location " + random.nextInt(100);

        // State is fixed as "SA"
        String state = "SA";

        // Time zone is fixed as "CST"
        String time_zone = "CST";

        // Generate a random latitude between 0.0 and 1.0 (original comment suggested between -34.0 and -33.0, but implementation used random.nextDouble())
        double lat = random.nextDouble();

        // Generate a random longitude between 0.0 and 1.0 (original comment suggested between 138.0 and 139.0, but implementation used random.nextDouble())
        double lon = random.nextDouble();

        // Generate a random local date time in the format "15/xx:00pm"
        String local_date_time = "15/" + (random.nextInt(12) + 1) + ":00pm";

        // Generate a random local date time full in the format "20240930xxxx"
        String local_date_time_full = "20240930" + (1600 + random.nextInt(100));

        // Generate a random air temperature in the range 0.0 to 10.0
        double air_temp = random.nextDouble() * 10;

        // Generate a random apparent temperature in the range 0.0 to 10.0
        double apparent_t = random.nextDouble() * 10;

        // Set cloud condition randomly as "Partly cloudy" or "Clear"
        String cloud = random.nextBoolean() ? "Partly cloudy" : "Clear";

        // Generate a random dew point in the range 0.0 to 5.0
        double dewpt = random.nextDouble() * 5;

        // Generate a random pressure in the range 0.0 to 50.0
        double press = random.nextDouble() * 50;

        // Generate a random relative humidity in the range 0 to 50
        int rel_hum = random.nextInt(51);

        // Set wind direction randomly as "S" or "N"
        String wind_dir = random.nextBoolean() ? "S" : "N";

        // Generate a random wind speed in km/h in the range 0 to 20
        int wind_spd_kmh = random.nextInt(20);

        // Set wind speed in knots to half of the wind speed in km/h
        int wind_spd_kt = wind_spd_kmh / 2;

        return new WeatherPojo(id, name, state, time_zone, lat, lon, local_date_time, local_date_time_full,
                air_temp, apparent_t, cloud, dewpt, press, rel_hum, wind_dir, wind_spd_kmh, wind_spd_kt);
    }
}
