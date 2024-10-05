package pojo;

/**
 * Wrapper class for WeatherPojo that includes a last update time field.
 * This class is used to add additional metadata (lastUpdateTime and versionId)
 * to the WeatherPojo object without modifying the original class.
 */
public class WeatherWrapperPojo {

    // The original WeatherPojo object containing weather information
    private pojo.WeatherPojo weatherInfo;

    // The timestamp representing the last time the weather information was updated
    private long lastUpdateTime;

    // The version ID for tracking updates
    private int versionId;


    public WeatherWrapperPojo() {}
    /**
     * Constructs a WeatherInfoWrapperDTO with the provided WeatherPojo object.
     * Initializes the lastUpdateTime field to the current system time and versionId to 0.
     *
     * @param weatherInfo the WeatherPojo object to wrap
     */
    public WeatherWrapperPojo(pojo.WeatherPojo weatherInfo, int versionId) {
        this.weatherInfo = weatherInfo;
        this.lastUpdateTime = System.currentTimeMillis();
        this.versionId = versionId;
    }

    public WeatherWrapperPojo(pojo.WeatherPojo weatherInfo, long lastUpdateTime) {
        this.weatherInfo = weatherInfo;
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Retrieves the wrapped WeatherPojo object.
     *
     * @return the WeatherPojo object
     */
    public pojo.WeatherPojo getWeatherInfo() {
        return weatherInfo;
    }

    /**
     * Sets the wrapped WeatherPojo object.
     *
     * @param weatherInfo the new WeatherPojo object
     */
    public void setWeatherInfo(pojo.WeatherPojo weatherInfo) {
        this.weatherInfo = weatherInfo;
    }

    /**
     * Retrieves the last update time of the WeatherPojo object.
     *
     * @return the timestamp of the last update as a long value
     */
    public Long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Sets the last update time for the WeatherPojo object.
     *
     * @param lastUpdateTime the new last update time as a Long value
     */
    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Retrieves the current version ID of the WeatherPojo object.
     *
     * @return the version ID as an integer
     */
    public int getVersionId() {
        return versionId;
    }

    /**
     * Sets the version ID for the WeatherPojo object.
     *
     * @param versionId the new version ID
     */
    public void setVersionId(int versionId) {
        this.versionId = versionId;
    }

}
