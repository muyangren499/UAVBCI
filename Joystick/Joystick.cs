﻿using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Threading;

namespace UAV.Joystick
{
    public class Joystick
    {
		public Dictionary<byte, bool> ButtonStates { get; private set; } = new Dictionary<byte, bool>();
		public Dictionary<byte, float> AxisValues { get; private set; } = new Dictionary<byte, float>();

        public event JoystickInputDelegate InputReceived;

        private bool running = false;
		private string deviceFile = null;

        private ConcurrentQueue<JoystickEventArgs> changes = new ConcurrentQueue<JoystickEventArgs>();

        public void Initialize(string deviceFile)
		{
			this.deviceFile = deviceFile;
			running = true;
			new Thread(InputThread).Start();
		}

        public void ProcessChanges()
        {
            JoystickEventArgs e = null;
			while (!changes.IsEmpty)
            {
                if(changes.TryDequeue(out e))
                {
                    if(e.IsButtonEvent)
                    {
						if (ButtonStates.ContainsKey(e.Button))
						{
							ButtonStates[e.Button] = e.IsPressed;
						}
						else
						{
							ButtonStates.Add(e.Button, e.IsPressed);
						}
                    }
                    else
                    {
						if (AxisValues.ContainsKey(e.Axis))
						{
							AxisValues[e.Axis] = e.Value;
						}
						else
						{
							AxisValues.Add(e.Axis, e.Value);
						}
                    }

                    if (InputReceived != null)
                    {
                        InputReceived(this, e);
                    }
                }
            }
        }

        private void InputThread()
        {
			using(FileStream inputStream = new FileStream(deviceFile, FileMode.Open))
			{
	            byte[] buff = new byte[8];
	            while (running)
	            {
	                inputStream.Read(buff, 0, 8);
	                JoystickEventArgs joystickEvent = DecodeJoystickEvent(buff);
	                if(joystickEvent != null)
	                {
	                    changes.Enqueue(joystickEvent);
						//InputReceived(this, joystickEvent);
	                }
				}
			}
        }

        private JoystickEventArgs DecodeJoystickEvent(byte[] buff)
        {
            if(checkBit(buff[6], (byte)MODE.CONFIGURATION))
            {
                if(checkBit(buff[6], (byte)TYPE.AXIS))
                {
                    return new JoystickEventArgs() { Axis = buff[7], IsButtonEvent = false, Value = 0 };
                }
                else if(checkBit(buff[6], (byte)TYPE.BUTTON))
                {
                    return new JoystickEventArgs() { Button = buff[7], IsButtonEvent = true, IsPressed = false };
                }
                else
                {
                    return null;
                }
            }
            else
            {
                if (checkBit(buff[6], (byte)TYPE.AXIS))
                {
					short rawValue = BitConverter.ToInt16(buff, 4);
					float value = ((float)rawValue) / (float)ushort.MaxValue;
					value *= 2;
                    return new JoystickEventArgs() { Axis = buff[7], IsButtonEvent = false, Value = value };
                }
                else if (checkBit(buff[6], (byte)TYPE.BUTTON))
                {
                    bool value = buff[4] == (byte)STATE.PRESSED;
                    return new JoystickEventArgs() { Button = buff[7], IsButtonEvent = true, IsPressed = value };
                }
                else
                {
                    return null;
                }
            }
        }

        enum STATE : byte { PRESSED = 0x01, RELEASED = 0x00 }
        enum TYPE : byte { AXIS = 0x02, BUTTON = 0x01 }
        enum MODE : byte { CONFIGURATION = 0x80, VALUE = 0x00 }

        bool checkBit(byte value, byte flag)
        {
            byte c = (byte)(value & flag);
            return c == (byte)flag;
        }
    }

    public delegate void JoystickInputDelegate(object sender, JoystickEventArgs e);

    public class JoystickEventArgs : EventArgs
    {
        public bool IsButtonEvent { get; internal set; }

        public float Value { get; internal set; }
        public byte Axis { get; internal set; }

        public bool IsPressed { get; internal set; }
        public byte Button { get; internal set; }
    }
    
    class Program
    {
        static void Main(string[] args)
        {
            // Checks if device parameter is defined properly.
            string deviceFile = "";
            if (args.Length == 2 && args[0] == "-d")
            {
                deviceFile = args[1];
            }
            else
            {
                Console.WriteLine("Wrong parameters. Example: ' -d /dev/input/js0'");
                return;
            }

            // Device file from parameter.
            Console.Clear();
            Console.WriteLine(string.Format("Device file: {0}", deviceFile));

            // Check if it exist.
            if (!File.Exists(deviceFile))
            {
                Console.WriteLine("Does not exists !");
                return;
            }

            // Read loop.
            using (FileStream fs = new FileStream(deviceFile, FileMode.Open))
            {
                byte[] buff = new byte[8];
                Joystick j = new Joystick();

                while (true)
                {
                    // Read 8 bytes from file and analyze.
                    fs.Read(buff, 0, 8);
                    j.DetectChange(buff);

                    int top = 1;

                    // Prints Axis values
                    foreach (byte key in j.Axis.Keys)
                    {
                        writeLine(top, string.Format("Axis{0}: {1}", key, j.Axis[key]));
                        top += 1;
                    }

                    // Prints Buttons values
                    foreach (byte key in j.Button.Keys)
                    {
                        writeLine(top, string.Format("Button{0}: {1}", key, j.Button[key]));
                        top += 1;
                    }
                }
            }
        }

        static void writeLine(int top, string value)
        {
            if (top < Console.BufferHeight)
            {
                Console.SetCursorPosition(0, top);
                Console.WriteLine(value.PadRight(Console.BufferWidth, ' '));
            }
        }

        public class Joystick
        {
            public Joystick()
            {
                Button = new Dictionary<byte, bool>();
                Axis = new Dictionary<byte, short>();
            }

            enum STATE : byte { PRESSED = 0x01, RELEASED = 0x00 }
            enum TYPE : byte { AXIS = 0x02, BUTTON = 0x01 }
            enum MODE : byte { CONFIGURATION = 0x80, VALUE = 0x00 }

            /// <summary>
            /// Buttons collection, key: address, bool: value
            /// </summary>
            public Dictionary<byte, bool> Button;

            /// <summary>
            /// Axis collection, key: address, short: value
            /// </summary>
            public Dictionary<byte, short> Axis;

            /// <summary>
            /// Function recognizes flags in buffer and modifies value of button, axis or configuration.
            /// Every new buffer changes only one value of one button/axis. Joystick object have to remember all previous values.
            /// </summary>
            public void DetectChange(byte[] buff)
            {
                // If configuration
                if (checkBit(buff[6], (byte)MODE.CONFIGURATION))
                {
                    if (checkBit(buff[6], (byte)TYPE.AXIS))
                    {
                        // Axis configuration, read address and register axis
                        byte key = (byte)buff[7];
                        if (!Axis.ContainsKey(key))
                        {
                            Axis.Add(key, 0);
                            return;
                        }
                    }
                    else if (checkBit(buff[6], (byte)TYPE.BUTTON))
                    {
                        // Button configuration, read address and register button
                        byte key = (byte)buff[7];
                        if (!Button.ContainsKey(key))
                        {
                            Button.Add((byte)buff[7], false);
                            return;
                        }
                    }
                }

                // If new button/axis value
                if (checkBit(buff[6], (byte)TYPE.AXIS))
                {
                    // Axis value, decode U2 and save to Axis dictionary.
                    short value = BitConverter.ToInt16(new byte[2] { buff[4], buff[5] }, 0);
                    Axis[(byte)buff[7]] = value;
                    return;
                }
                else if (checkBit(buff[6], (byte)TYPE.BUTTON))
                {
                    // Bytton value, decode value and save to Button dictionary.
                    Button[(byte)buff[7]] = buff[4] == (byte)STATE.PRESSED;
                    return;
                }
            }

            /// <summary>
            /// Checks if bits that are set in flag are set in value.
            /// </summary>
            bool checkBit(byte value, byte flag)
            {
                byte c = (byte)(value & flag);
                return c == (byte)flag;
            }
        }
    }

    
}