% Specify file path
filename = "C:\Users\joel2\Documents\Skola\Ak6\SignalProcessing\audio\signal_lab_recording_1769184656281.raw";
% Open file for reading
fid = fopen(filename, 'r');
% Read all samples as int16 (16-bit signed, little endian)
data = fread(fid, 'int16', 0, 'ieee-le');
% Close the file
fclose(fid);
% Normalize to [-1, 1] for floating point
audio = double(data) / 32768;

Fs = 44100;
player = audioplayer(audio, Fs);
t = (0:size(audio,1)-1) / Fs;
figure;
hLine = plot(t, audio(:,1)); hold on;                % waveform
hMarker = plot(0, 0, 'ro', 'MarkerFaceColor','r');  % moving marker
xlabel('Time (s)'); ylabel('Amplitude');

% Timer callback: update marker based on CurrentSample
player.TimerPeriod = 0.05;    % update every 50 ms
player.TimerFcn = @(~,~) updateMarker(player, hMarker, t, audio);

% helper function (place at end of file or as separate file)
function updateMarker(playerObj, hMarker, tVec, audioData)
    if strcmp(playerObj.Running,'on')
        cs = playerObj.CurrentSample;
        cs = min(cs, size(audioData,1));
        set(hMarker, 'XData', tVec(cs), 'YData', audioData(cs,1));
        drawnow limitrate;
    end
end

play(player);   % start playback and marker updates