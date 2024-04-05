% Open serial port
s = serial('COM3'); % Change COM port as per your configuration
set(s, 'BaudRate', 9600); % Set baud rate
fopen(s);

% Generate and send ECG signal continuously
while true
    % Generate ECG signal
    t = linspace(0, 10, 1000); % Time vector (10 seconds)
    ecg_signal = sin(2*pi*1*t) + 0.5*sin(2*pi*2*t); % Simple ECG signal example

    % Send ECG signal over serial in comma-separated format
    for i = 1:length(ecg_signal)
        fprintf(s, '%f,', ecg_signal(i)); % Send each data point with a comma separator
    end
    fprintf(s, '\n'); % End the line with a newline character
    pause(0.01); % Pause for a short duration (adjust as needed)
end

% Close serial port (this code will never be reached if the loop runs indefinitely)
fclose(s);
delete(s);
clear s;
