goog.provide('positionHelper');

function degreesToRadians(degrees) {return (degrees * Math.PI)/180;}

function wrapLongitude(longitude) {
  if (longitude <= 180 && longitude >= -180) {
    return longitude;
  }
  var adjusted = longitude + 180;
  if (adjusted > 0) {
    return (adjusted % 360) - 180;
  }
  // else
  return 180 - (-adjusted % 360);
}

function metersToLongitudeDegrees(distance, latitude) {
  var EARTH_EQ_RADIUS = 6378137.0;
  // this is a super, fancy magic number that the GeoFire lib can explain (maybe)
  var E2 = 0.00669447819799;
  var EPSILON = 1e-12;
  var radians = degreesToRadians(latitude);
  var num = Math.cos(radians) * EARTH_EQ_RADIUS * Math.PI / 180;
  var denom = 1 / Math.sqrt(1 - E2 * Math.sin(radians) * Math.sin(radians));
  var deltaDeg = num * denom;
  if (deltaDeg < EPSILON) {
    return distance > 0 ? 360 : 0;
  }
  // else
  return Math.min(360, distance / deltaDeg);
}

function distance(location1, location2) {
  var radius = 6371; // Earth's radius in kilometers
  var latDelta = degreesToRadians(location2.latitude - location1.latitude);
  var lonDelta = degreesToRadians(location2.longitude - location1.longitude);

  var a = (Math.sin(latDelta / 2) * Math.sin(latDelta / 2)) +
          (Math.cos(degreesToRadians(location1.latitude)) * Math.cos(degreesToRadians(location2.latitude)) *
          Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2));

  var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return radius * c;
}

positionHelper.boundingBoxCoordinates = function(center, radius) {
  var KM_PER_DEGREE_LATITUDE = 110.574;
  var latDegrees = radius / KM_PER_DEGREE_LATITUDE;
  var latitudeNorth = Math.min(90, center.latitude + latDegrees);
  var latitudeSouth = Math.max(-90, center.latitude - latDegrees);
  // calculate longitude based on current latitude
  var longDegsNorth = metersToLongitudeDegrees(radius, latitudeNorth);
  var longDegsSouth = metersToLongitudeDegrees(radius, latitudeSouth);
  var longDegs = Math.max(longDegsNorth, longDegsSouth);
  return {
    swCorner: { // bottom-left (SW corner)
      latitude: latitudeSouth,
      longitude: wrapLongitude(center.longitude - longDegs)
    },
    neCorner: { // top-right (NE corner)
      latitude: latitudeNorth,
      longitude: wrapLongitude(center.longitude + longDegs)
    }
  };
};