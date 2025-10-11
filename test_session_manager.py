#!/usr/bin/env python3
"""
Test script to verify SessionDataManager functionality.
This simulates what the Android app would do when reading session data.
"""

import os
import json
from datetime import datetime

def test_session_discovery():
    """Test session discovery logic similar to SessionDataManager.getAllSessions()"""
    
    test_data_dir = "test_data/ClashRoyaleLogger"
    
    if not os.path.exists(test_data_dir):
        print("❌ Test data directory not found!")
        return
    
    print("🔍 Testing Session Discovery...")
    print(f"📁 Looking in: {os.path.abspath(test_data_dir)}")
    
    # Get all files in directory
    files = os.listdir(test_data_dir)
    print(f"📄 Found {len(files)} files")
    
    # Extract session IDs (similar to SessionDataManager logic)
    session_ids = set()
    for file in files:
        if '_' in file and '.' in file:
            # Extract session ID (everything before the last underscore and file extension)
            parts = file.split('_')
            if len(parts) >= 2:
                session_id = '_'.join(parts[:-1])
                session_ids.add(session_id)
    
    print(f"🎯 Discovered {len(session_ids)} unique sessions:")
    
    sessions = []
    for session_id in sorted(session_ids):
        print(f"\n📊 Session: {session_id}")
        
        # Check for required files
        csv_file = f"{session_id}_touches.csv"
        metadata_file = f"{session_id}_metadata.json"
        video_file = f"{session_id}_video.mp4"
        
        csv_exists = csv_file in files
        metadata_exists = metadata_file in files
        video_exists = video_file in files
        
        print(f"  📄 CSV: {'✅' if csv_exists else '❌'} {csv_file}")
        print(f"  📄 Metadata: {'✅' if metadata_exists else '❌'} {metadata_file}")
        print(f"  📄 Video: {'✅' if video_exists else '❌'} {video_file}")
        
        # Read metadata if available
        if metadata_exists:
            try:
                with open(os.path.join(test_data_dir, metadata_file), 'r') as f:
                    metadata = json.load(f)
                
                print(f"  📅 Start: {metadata.get('recording_start', 'N/A')}")
                print(f"  📅 End: {metadata.get('recording_end', 'N/A')}")
                print(f"  ⏱️ Duration: {metadata.get('total_duration_ms', 'N/A')} ms")
                print(f"  👆 Touch Events: {metadata.get('touch_events_count', 'N/A')}")
                
            except Exception as e:
                print(f"  ❌ Error reading metadata: {e}")
        
        # Calculate file sizes
        total_size = 0
        if csv_exists:
            csv_size = os.path.getsize(os.path.join(test_data_dir, csv_file))
            total_size += csv_size
            print(f"  📏 CSV Size: {csv_size:,} bytes")
        
        if metadata_exists:
            metadata_size = os.path.getsize(os.path.join(test_data_dir, metadata_file))
            total_size += metadata_size
            print(f"  📏 Metadata Size: {metadata_size:,} bytes")
        
        if video_exists:
            video_size = os.path.getsize(os.path.join(test_data_dir, video_file))
            total_size += video_size
            print(f"  📏 Video Size: {video_size:,} bytes")
        
        print(f"  📏 Total Size: {total_size:,} bytes ({total_size/1024:.1f} KB)")
        
        # Determine completeness
        is_complete = csv_exists and metadata_exists and video_exists
        print(f"  ✅ Status: {'Complete' if is_complete else 'Incomplete'}")
        
        sessions.append({
            'session_id': session_id,
            'is_complete': is_complete,
            'total_size': total_size,
            'has_csv': csv_exists,
            'has_metadata': metadata_exists,
            'has_video': video_exists
        })
    
    print(f"\n📈 Summary:")
    complete_sessions = [s for s in sessions if s['is_complete']]
    incomplete_sessions = [s for s in sessions if not s['is_complete']]
    total_size = sum(s['total_size'] for s in sessions)
    
    print(f"  ✅ Complete Sessions: {len(complete_sessions)}")
    print(f"  ⚠️ Incomplete Sessions: {len(incomplete_sessions)}")
    print(f"  📏 Total Data Size: {total_size:,} bytes ({total_size/1024:.1f} KB)")
    
    return sessions

def test_export_simulation():
    """Simulate export functionality"""
    print(f"\n📦 Testing Export Simulation...")
    
    # This would be similar to SessionDataManager.exportSessions()
    test_data_dir = "test_data/ClashRoyaleLogger"
    export_dir = "test_exports"
    os.makedirs(export_dir, exist_ok=True)
    
    sessions = test_session_discovery()
    complete_sessions = [s for s in sessions if s['is_complete']]
    
    if complete_sessions:
        print(f"📦 Would export {len(complete_sessions)} complete sessions")
        print(f"📁 Export directory: {os.path.abspath(export_dir)}")
        
        # Simulate creating a ZIP file
        zip_filename = f"sessions_export_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip"
        print(f"📦 Would create: {zip_filename}")
        
        for session in complete_sessions:
            print(f"  📄 Would include session: {session['session_id']}")
    else:
        print("❌ No complete sessions to export")

if __name__ == "__main__":
    print("🧪 SessionDataManager Test")
    print("=" * 50)
    
    test_session_discovery()
    test_export_simulation()
    
    print("\n✅ Test completed!")