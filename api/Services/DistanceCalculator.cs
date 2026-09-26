namespace SolarGrid.Api.Services;

public static class DistanceCalculator
{
    public static double Kilometres(
        double firstLatitude,
        double firstLongitude,
        double secondLatitude,
        double secondLongitude)
    {
        const double earthRadius = 6371;

        var latitudeDifference =
            DegreesToRadians(secondLatitude - firstLatitude);

        var longitudeDifference =
            DegreesToRadians(secondLongitude - firstLongitude);

        var value =
            Math.Sin(latitudeDifference / 2) *
            Math.Sin(latitudeDifference / 2) +
            Math.Cos(DegreesToRadians(firstLatitude)) *
            Math.Cos(DegreesToRadians(secondLatitude)) *
            Math.Sin(longitudeDifference / 2) *
            Math.Sin(longitudeDifference / 2);

        var distance = 2 * Math.Atan2(
            Math.Sqrt(value),
            Math.Sqrt(1 - value));

        return earthRadius * distance;
    }

    private static double DegreesToRadians(double degrees)
    {
        return degrees * Math.PI / 180;
    }
}