import com.google.gson.Gson;
import pojo.WeatherPojo;
import utils.CreateWeatherInfoUtil;
import utils.JsonHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonParseTest {

    Gson gson = new Gson();


    @Test
    public void testJsonParse() throws IllegalAccessException, InstantiationException {
        // Test example
        WeatherPojo weatherInfoDTO = CreateWeatherInfoUtil.createMockWeatherInfo();
        String a = JsonHelper.toJson(weatherInfoDTO);
        String b = gson.toJson(weatherInfoDTO);
        System.out.println(a);


        System.out.println(b);


    }

    @Test
    public void testFromJson() throws IllegalAccessException, InstantiationException {

        String jsonString = "{\"id\":\"IDS60461\",\"name\":\"Location 26\",\"state\":\"SA\",\"time_zone\":\"CST\",\"lat\":0.7109235518555799,\"lon\":0.9438878370565853,\"local_date_time\":\"15/8:00pm\",\"local_date_time_full\":\"202409301615\",\"air_temp\":4.077108962352228,\"apparent_t\":6.536788431169587,\"cloud\":\"Clear\",\"dewpt\":3.6820549153899473,\"press\":19.596664989348984,\"rel_hum\":45,\"wind_dir\":\"N\",\"wind_spd_kmh\":12,\"wind_spd_kt\":6}";

        WeatherPojo weatherInfoDTO1 = gson.fromJson(jsonString, WeatherPojo.class);
        WeatherPojo weatherInfoDTO2 = JsonHelper.fromJson(jsonString, WeatherPojo.class);
        System.out.println(weatherInfoDTO1.toString().equals(weatherInfoDTO2.toString()));
        System.out.println(weatherInfoDTO1.toString());
        System.out.println(weatherInfoDTO2.toString());
    }

    /**
     * Test the fromJson method with valid input.
     *
     */
    @Test
    public void testFromJson_ValidInput() throws IllegalAccessException, InstantiationException {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":23.5,\"wind_speed\":\"10km/h\"}";
        WeatherPojo wd = JsonHelper.fromJson(input, WeatherPojo.class);
        assertEquals("IDS60901", wd.getId());
        assertEquals("Adelaide", wd.getName());
    }

    /**
     * Test the fromJson method with invalid input.
     *
     */
    @Test
    public void testFromJson_InvalidInput() {
        String input = "IDS60901,Adelaide,air_temp:23.5,wind_speed:10km/h";
        assertThrows(IllegalArgumentException.class, () -> JsonHelper.fromJson(input, WeatherPojo.class));
    }

    /**
     * Test the fromJson method with an empty value.
     *
     */
    @Test
    public void testFromJson_EmptyValue() {
        String input = "{\"id\":\"IDS60901\",\"name\":\"Adelaide\",\"air_temp\":\"\"}";
        assertThrows(NumberFormatException.class, () -> JsonHelper.fromJson(input, WeatherPojo.class));
    }

}
