# Clash Royale Logger - Synchronization Improvements

## Overview
This document outlines the improvements made to synchronize click data and footage data in the Clash Royale logging application.

## Key Improvements

### 1. Precise Timestamp Synchronization
- **Recording Start Time Tracking**: Added `recordingStartTime` and `recordingStartRealTime` to track exact recording start moments
- **Video Start Marking**: Implemented `markVideoStart()` to precisely mark when video recording begins
- **Relative Time Calculation**: Touch events now include `RelativeTime_ns` (nanoseconds since recording start)

### 2. Enhanced Touch Event Logging
- **Actual Coordinates**: Touch events now capture real x,y coordinates from AccessibilityEvent sources
- **Additional Touch Data**: Added pressure and size information to touch events
- **Multiple Event Types**: Captures TOUCH_DOWN, TOUCH_UP, CLICK, LONG_CLICK, GESTURE_START, GESTURE_END, and APP_CONTEXT events

### 3. Metadata File System
- **JSON Metadata**: Creates `session_[timestamp]_metadata.json` files alongside CSV logs
- **Synchronization Data**: Metadata includes:
  - Recording start time
  - Video start time
  - Touch event timestamps with video correlation
  - Session duration and end time

### 4. Improved User Interface
- **Sync Status Display**: Shows current synchronization status
- **Session Information**: Displays information about current and previous recording sessions
- **Enhanced Visual Feedback**: Better status indicators and recording state display

## File Structure
```
/storage/emulated/0/TouchLogger/
├── session_[timestamp]_touches.csv     # Touch event data
├── session_[timestamp]_metadata.json   # Synchronization metadata
└── session_[timestamp]_video.mp4       # Screen recording
```

## Data Format

### CSV Touch Data
```
Timestamp,RelativeTime_ns,EventType,X,Y,Pressure,Size
1704067200000,0,TOUCH_DOWN,500.0,300.0,1.0,1.0
1704067200100,100000000,TOUCH_UP,500.0,300.0,1.0,1.0
```

### JSON Metadata
```json
{
  "session_start": 1704067200000,
  "recording_start_time": 1704067200050,
  "video_start_time": 1704067200100,
  "events": [
    {
      "type": "TOUCH_DOWN",
      "relative_time_ns": 0,
      "timestamp": 1704067200000,
      "video_time_ms": 50,
      "x": 500.0,
      "y": 300.0
    }
  ],
  "session_end": 1704067300000,
  "total_duration_ms": 100000
}
```

## Synchronization Benefits

1. **Frame-Perfect Alignment**: Touch events can be precisely correlated with video frames
2. **Data Analysis**: Enhanced data structure enables better analysis of user interactions
3. **Debugging Support**: Metadata provides comprehensive debugging information
4. **Export Compatibility**: Data format suitable for analysis tools and machine learning pipelines

## Usage Instructions

1. **Enable Accessibility Service**: Go to Settings > Accessibility > TouchLoggerService
2. **Grant Screen Recording Permission**: Allow the app to record screen content
3. **Start Recording**: Tap "Start Recording" button
4. **Perform Actions**: Interact with the screen normally
5. **Stop Recording**: Tap "Stop Recording" button
6. **Access Data**: Files are saved in `/storage/emulated/0/TouchLogger/`

## Technical Notes

- Minimum Android API: 26 (Android 8.0)
- Requires RECORD_AUDIO and WRITE_EXTERNAL_STORAGE permissions
- Uses MediaProjection API for screen recording
- Implements AccessibilityService for touch event capture
- Foreground service ensures continuous recording capability