# Data Storage and Management System

## Overview

The ClashRoyaleLogger app now includes a comprehensive data storage and management system that allows users to easily view, export, and manage their recorded sessions directly within the app.

## Features

### 📱 **Data Management Activity**
- **Modern UI**: Clean, Material Design 3 interface with CardView layouts
- **Session List**: RecyclerView displaying all recorded sessions with detailed information
- **Real-time Stats**: Shows total session count and storage usage
- **Empty State**: Friendly message when no sessions are found

### 📊 **Session Information Display**
Each session shows:
- **Date & Time**: When the recording was made
- **Status**: Complete ✅ or Incomplete ⚠️ with color coding
- **Duration**: Total recording time
- **Touch Events**: Number of captured touch interactions
- **File Size**: Total storage used by the session
- **File Status**: Which files are present (CSV, Metadata, Video)

### 📦 **Export Functionality**
- **Individual Export**: Export single sessions as ZIP files
- **Bulk Export**: Export all complete sessions at once
- **Share Integration**: Uses Android's built-in sharing system
- **ZIP Format**: Compressed archives containing all session files

### 🗑️ **Session Management**
- **Individual Delete**: Remove specific sessions
- **Bulk Delete**: Clear all sessions with confirmation
- **Safe Deletion**: Confirmation dialogs prevent accidental data loss
- **Automatic Cleanup**: Removes all associated files

## File Structure

Each recording session generates three synchronized files:

```
/storage/emulated/0/ClashRoyaleLogger/
├── 20241005_143022_touches.csv      # Touch event data
├── 20241005_143022_metadata.json   # Session metadata
└── 20241005_143022_video.mp4       # Screen recording
```

### 📄 **CSV File Format**
```csv
timestamp,event_type,x,y,pressure,size,package_name
2024-10-05 14:30:22.123,TOUCH_DOWN,540,960,0.8,0.15,com.supercell.clashroyale
2024-10-05 14:30:22.145,TOUCH_UP,540,960,0.0,0.15,com.supercell.clashroyale
```

### 📄 **Metadata JSON Format**
```json
{
  "session_id": "20241005_143022",
  "recording_start": "2024-10-05 14:30:22.123",
  "recording_end": "2024-10-05 14:30:45.678",
  "video_start": "2024-10-05 14:30:22.323",
  "total_duration_ms": 23555,
  "touch_events_count": 127,
  "video_file": "20241005_143022_video.mp4",
  "csv_file": "20241005_143022_touches.csv",
  "device_info": {
    "model": "Pixel 7",
    "android_version": "14",
    "screen_resolution": "1080x2400"
  }
}
```

## Technical Implementation

### 🏗️ **Architecture Components**

1. **SessionData.kt**: Data class representing a recording session
2. **SessionDataManager.kt**: Core logic for session discovery, loading, and export
3. **SessionAdapter.kt**: RecyclerView adapter for displaying sessions
4. **DataManagementActivity.kt**: Main activity for data management UI

### 🔍 **Session Discovery Algorithm**
```kotlin
fun getAllSessions(): List<SessionData> {
    val storageDir = getStorageDirectory()
    val files = storageDir.listFiles() ?: return emptyList()
    
    // Extract unique session IDs from filenames
    val sessionIds = files.mapNotNull { file ->
        extractSessionId(file.name)
    }.toSet()
    
    // Create SessionData objects for each discovered session
    return sessionIds.mapNotNull { sessionId ->
        loadSessionData(sessionId)
    }.sortedByDescending { it.timestamp }
}
```

### 📦 **Export Implementation**
```kotlin
fun exportSessions(sessions: List<SessionData>): File {
    val zipFile = File(cacheDir, "sessions_export_${timestamp}.zip")
    
    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        sessions.forEach { session ->
            // Add CSV file
            addFileToZip(zip, session.csvFile, "${session.sessionId}_touches.csv")
            // Add metadata file
            addFileToZip(zip, session.metadataFile, "${session.sessionId}_metadata.json")
            // Add video file
            addFileToZip(zip, session.videoFile, "${session.sessionId}_video.mp4")
        }
    }
    
    return zipFile
}
```

## User Interface

### 📱 **Main Screen Updates**
- Added "View Recorded Data" button below the recording controls
- Button uses Material Design 3 outlined style with icon
- Seamless navigation to data management screen

### 📊 **Data Management Screen**
- **Header**: Title and refresh button
- **Stats Bar**: Session count and total storage usage
- **Action Buttons**: Export All and Delete All
- **Session List**: Scrollable list of all sessions
- **Empty State**: Shown when no sessions exist

### 🎨 **Visual Design**
- **Material Design 3**: Modern, consistent styling
- **Color Coding**: Green for complete sessions, orange for incomplete
- **Icons**: Intuitive icons for all actions (view, export, delete)
- **Cards**: Elevated cards for each session with proper spacing
- **Typography**: Clear hierarchy with appropriate text sizes

## Usage Instructions

### 📱 **Accessing Data Management**
1. Open the ClashRoyaleLogger app
2. Tap "View Recorded Data" button on the main screen
3. The data management screen will open showing all sessions

### 👀 **Viewing Sessions**
- Sessions are listed chronologically (newest first)
- Each card shows session date, status, duration, and file information
- Complete sessions show green status, incomplete show orange
- Tap "View" to see detailed session information

### 📦 **Exporting Data**
- **Single Session**: Tap "Export" on any complete session
- **All Sessions**: Tap "Export All" at the top of the screen
- Choose sharing method (email, cloud storage, etc.)
- ZIP file contains all session files with organized naming

### 🗑️ **Deleting Sessions**
- **Single Session**: Tap "Delete" on any session
- **All Sessions**: Tap "Delete All" at the top of the screen
- Confirm deletion in the dialog that appears
- All associated files are permanently removed

## Data Synchronization

### ⏱️ **Timestamp Precision**
- Touch events and video frames are precisely synchronized
- Metadata file contains exact video start time
- Millisecond-level accuracy for frame-perfect analysis

### 📊 **Data Quality Indicators**
- Session completeness status (all files present)
- Touch event count validation
- File size verification
- Duration consistency checks

## Storage Management

### 📁 **Storage Location**
- Primary: `/storage/emulated/0/ClashRoyaleLogger/`
- Fallback: App's external files directory
- Automatic directory creation

### 💾 **Storage Optimization**
- Efficient file naming convention
- Compressed video files
- Minimal metadata overhead
- Automatic cleanup of incomplete sessions

## Testing and Validation

### 🧪 **Test Data Generation**
The project includes test scripts for validation:
- `create_test_data.py`: Generates sample session data
- `test_session_manager.py`: Validates data management logic

### ✅ **Validation Results**
- ✅ Session discovery works correctly
- ✅ Complete/incomplete session detection
- ✅ File size calculation accurate
- ✅ Export functionality ready
- ✅ UI displays proper information

## Benefits

### 🎯 **For Users**
- **Easy Access**: View all recordings in one place
- **Quick Export**: Share data with researchers or for analysis
- **Storage Awareness**: See how much space recordings use
- **Data Control**: Delete unwanted sessions easily

### 🔬 **For Researchers**
- **Organized Data**: Structured file format for analysis
- **Complete Sessions**: Only export verified complete recordings
- **Metadata Rich**: Detailed information about each session
- **Analysis Ready**: CSV format compatible with data analysis tools

### 📱 **For Developers**
- **Modular Design**: Easy to extend and modify
- **Error Handling**: Robust handling of missing or corrupted files
- **Performance**: Efficient file operations and UI updates
- **Maintainable**: Clean separation of concerns

## Future Enhancements

### 🚀 **Potential Improvements**
- **Cloud Sync**: Automatic backup to cloud storage
- **Data Analytics**: Built-in analysis and visualization
- **Filtering**: Search and filter sessions by date, duration, etc.
- **Compression**: Advanced compression for larger datasets
- **Batch Operations**: More bulk management features

---

*This data management system transforms the ClashRoyaleLogger from a simple recording tool into a comprehensive data collection and analysis platform.*