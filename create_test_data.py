#!/usr/bin/env python3
"""
Script to create sample test data for the Android app's data management functionality.
This creates mock session files in the expected format.
"""

import os
import json
import csv
import random
import argparse
from pathlib import Path
from datetime import datetime, timedelta


def create_sample_session(session_id: str, base_time: datetime, out_dir: Path) -> None:
    """Create a complete sample session with all required files in ``out_dir``."""

    # Create CSV file with sample touch events
    csv_file = out_dir / f"{session_id}_touches.csv"
    with open(csv_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['timestamp', 'event_type', 'x', 'y', 'pressure', 'size', 'package_name'])

        # Generate 50 sample touch events
        for i in range(50):
            timestamp = base_time + timedelta(milliseconds=i * 100)
            event_type = random.choice(['TOUCH_DOWN', 'TOUCH_UP', 'CLICK', 'LONG_CLICK'])
            x = random.randint(100, 1000)
            y = random.randint(200, 1800)
            pressure = round(random.uniform(0.1, 1.0), 2)
            size = round(random.uniform(0.05, 0.3), 2)
            package_name = "com.supercell.clashroyale"

            writer.writerow([
                timestamp.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
                event_type, x, y, pressure, size, package_name
            ])

    # Create metadata JSON file
    metadata_file = out_dir / f"{session_id}_metadata.json"
    metadata = {
        "session_id": session_id,
        "recording_start": base_time.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
        "recording_end": (base_time + timedelta(seconds=5)).strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
        "video_start": (base_time + timedelta(milliseconds=200)).strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
        "total_duration_ms": 5000,
        "touch_events_count": 50,
        "video_file": f"{session_id}_video.mp4",
        "csv_file": f"{session_id}_touches.csv",
        "device_info": {
            "model": "Test Device",
            "android_version": "14",
            "screen_resolution": "1080x2400"
        }
    }

    with open(metadata_file, 'w') as f:
        json.dump(metadata, f, indent=2)

    # Create a dummy video file (just a placeholder)
    video_file = out_dir / f"{session_id}_video.mp4"
    with open(video_file, 'wb') as f:
        # Write some dummy data to simulate a video file
        f.write(b"DUMMY_VIDEO_DATA" * 1000)  # ~15KB file

    print(f"Created session: {session_id}")


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description="Create sample Clash Royale Logger test data.")
    parser.add_argument("--seed", type=int, default=None, help="Seed for deterministic random data.")
    parser.add_argument(
        "--out",
        type=str,
        default=None,
        help="Output directory (default: <repo>/test_data/ClashRoyaleLogger)",
    )
    args = parser.parse_args(argv)

    base_dir = Path(__file__).resolve().parent
    default_out = base_dir / "test_data" / "ClashRoyaleLogger"
    out_dir = Path(args.out) if args.out else default_out

    if args.seed is not None:
        random.seed(args.seed)

    # Ensure output directory exists (anchor to script dir by default)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Create 3 complete sample sessions
    base_time = datetime.now() - timedelta(hours=2)
    for i in range(3):
        session_time = base_time + timedelta(minutes=i * 30)
        session_id = session_time.strftime('%Y%m%d_%H%M%S')
        create_sample_session(session_id, session_time, out_dir)

    # Create one incomplete session (missing video file)
    incomplete_time = base_time + timedelta(hours=1, minutes=30)
    incomplete_id = incomplete_time.strftime('%Y%m%d_%H%M%S')

    # Create only CSV and metadata for incomplete session
    csv_file = out_dir / f"{incomplete_id}_touches.csv"
    with open(csv_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['timestamp', 'event_type', 'x', 'y', 'pressure', 'size', 'package_name'])
        writer.writerow([
            incomplete_time.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
            'TOUCH_DOWN', 500, 800, 0.8, 0.15, 'com.supercell.clashroyale'
        ])

    metadata_file = out_dir / f"{incomplete_id}_metadata.json"
    metadata = {
        "session_id": incomplete_id,
        "recording_start": incomplete_time.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
        "recording_end": None,
        "video_start": None,
        "total_duration_ms": None,
        "touch_events_count": 1,
        "video_file": f"{incomplete_id}_video.mp4",
        "csv_file": f"{incomplete_id}_touches.csv",
        "device_info": {
            "model": "Test Device",
            "android_version": "14",
            "screen_resolution": "1080x2400"
        }
    }

    with open(metadata_file, 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"Created incomplete session: {incomplete_id}")
    print(f"\nTest data created in: {out_dir.resolve()}")
    print("Files created:")
    for file in sorted(out_dir.iterdir()):
        if file.is_file():
            size = file.stat().st_size
            print(f"  {file.name} ({size} bytes)")


if __name__ == "__main__":
    main()
