package btools.routingapp;

import android.os.Bundle;

/** IPC interface matching the BRouter Android service. */
interface IBRouterService {
    String getTrackFromParams(in Bundle params);
}
